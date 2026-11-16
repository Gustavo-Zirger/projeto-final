package br.edu.projetofinal.reengagement;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;
import jakarta.mail.MessagingException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NotificationService {
    private static final Logger LOGGER =
            Logger.getLogger(NotificationService.class.getName());
    private final int retryDelayMs;
    private final int maxRetries;
    private final NotificationStore store;
    private final EmailSender sender;

    public NotificationService() throws Exception {
        retryDelayMs = Integer.parseInt(
                System.getenv().getOrDefault("RETRY_DELAY_MS", "30000"));
        maxRetries = Integer.parseInt(System.getenv().getOrDefault("MAX_RETRIES", "3"));
        if (retryDelayMs <= 0 || maxRetries < 0) {
            throw new IllegalArgumentException(
                    "RETRY_DELAY_MS deve ser positivo e MAX_RETRIES nao negativo");
        }
        store = new NotificationStore(System.getenv().getOrDefault(
                "SQLITE_PATH", "/data/notifications.sqlite3"));
        sender = new EmailSender();
    }

    public void run() throws InterruptedException {
        while (!Thread.currentThread().isInterrupted()) {
            try (Connection connection = RabbitConnections.connect();
                 Channel channel = connection.createChannel()) {
                declareQueues(channel);
                channel.basicQos(1);
                channel.confirmSelect();
                channel.basicConsume(
                        RabbitConnections.QUEUE,
                        false,
                        (consumerTag, delivery) -> handleDelivery(channel, delivery),
                        consumerTag -> LOGGER.info("Consumidor cancelado: " + consumerTag));
                LOGGER.info("Consumindo a fila " + RabbitConnections.QUEUE);
                while (connection.isOpen() && channel.isOpen()) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException exception) {
                throw exception;
            } catch (Exception exception) {
                LOGGER.log(Level.SEVERE,
                        "Consumidor indisponivel; nova conexao em 5 segundos", exception);
                Thread.sleep(5000);
            }
        }
    }

    private void handleDelivery(Channel channel, Delivery delivery) throws IOException {
        Reminder reminder;
        try {
            reminder = ReminderCodec.decode(delivery.getBody());
        } catch (IOException exception) {
            LOGGER.log(Level.SEVERE,
                    "Mensagem invalida enviada para a fila de falhas", exception);
            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            return;
        }

        long deliveryTag = delivery.getEnvelope().getDeliveryTag();
        try {
            if (!store.beginAttempt(reminder)) {
                LOGGER.info("Lembrete ja enviado; mensagem duplicada descartada");
                channel.basicAck(deliveryTag, false);
                return;
            }

            try {
                sender.send(reminder);
            } catch (MessagingException exception) {
                store.markFailed(reminder, exception);
                routeFailure(channel, delivery, exception);
                channel.basicAck(deliveryTag, false);
                return;
            }
            store.markSent(reminder);
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE,
                    "Falha ao processar notificacao; mensagem sera reencaminhada", exception);
            try {
                Thread.sleep(5000);
                channel.basicNack(deliveryTag, false, true);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrompido ao reencaminhar notificacao", interrupted);
            }
        }
    }

    private void routeFailure(Channel channel, Delivery delivery, MessagingException error)
            throws IOException {
        Map<String, Object> headers = new HashMap<>();
        Map<String, Object> existingHeaders = delivery.getProperties().getHeaders();
        if (existingHeaders != null) {
            headers.putAll(existingHeaders);
        }
        int retryCount = retryCount(headers.get("x-retry-count"));
        String targetQueue;
        if (retryCount < maxRetries) {
            headers.put("x-retry-count", retryCount + 1);
            targetQueue = RabbitConnections.RETRY_QUEUE;
        } else {
            targetQueue = RabbitConnections.FAILED_QUEUE;
            LOGGER.log(Level.SEVERE, "Limite de retentativas atingido", error);
        }
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .contentType("application/json")
                .headers(headers)
                .build();
        channel.basicPublish("", targetQueue, properties, delivery.getBody());
        try {
            channel.waitForConfirmsOrDie();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrompido ao publicar retentativa", interrupted);
        }
    }

    private static int retryCount(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private void declareQueues(Channel channel) throws IOException {
        channel.queueDeclare(RabbitConnections.FAILED_QUEUE, true, false, false, null);
        channel.queueDeclare(
                RabbitConnections.QUEUE, true, false, false,
                Map.of(
                        "x-dead-letter-exchange", "",
                        "x-dead-letter-routing-key", RabbitConnections.FAILED_QUEUE));
        channel.queueDeclare(
                RabbitConnections.RETRY_QUEUE, true, false, false,
                Map.of(
                        "x-message-ttl", retryDelayMs,
                        "x-dead-letter-exchange", "",
                        "x-dead-letter-routing-key", RabbitConnections.QUEUE));
    }
}
