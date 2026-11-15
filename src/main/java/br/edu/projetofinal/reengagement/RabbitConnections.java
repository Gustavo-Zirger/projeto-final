package br.edu.projetofinal.reengagement;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public final class RabbitConnections {
    public static final String QUEUE = "reengagement.notifications";
    public static final String RETRY_QUEUE = "reengagement.notifications.retry";
    public static final String FAILED_QUEUE = "reengagement.notifications.failed";

    private RabbitConnections() {
    }

    public static Connection connect() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(System.getenv().getOrDefault("RABBITMQ_HOST", "rabbitmq"));
        factory.setPort(Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672")));
        factory.setUsername(System.getenv().getOrDefault("RABBITMQ_USER", "reengagement"));
        factory.setPassword(System.getenv().getOrDefault(
                "RABBITMQ_PASSWORD", "dev-only-change-me"));
        factory.setConnectionTimeout(10_000);
        factory.setRequestedHeartbeat(30);
        return factory.newConnection("moodle-reengagement");
    }
}
