package br.edu.projetofinal.reengagement;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;

public final class EmailSender {
    private static final DateTimeFormatter DEADLINE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm 'UTC'", Locale.forLanguageTag("pt-BR"));

    private final String host;
    private final int port;
    private final String sender;
    private final String username;
    private final String password;
    private final boolean startTls;

    public EmailSender() {
        this.host = System.getenv().getOrDefault("SMTP_HOST", "mailpit");
        this.port = Integer.parseInt(System.getenv().getOrDefault("SMTP_PORT", "1025"));
        this.sender = System.getenv().getOrDefault(
                "SMTP_FROM", "lembretes@example.invalid");
        this.username = System.getenv().getOrDefault("SMTP_USERNAME", "");
        this.password = System.getenv().getOrDefault("SMTP_PASSWORD", "");
        this.startTls = Boolean.parseBoolean(
                System.getenv().getOrDefault("SMTP_STARTTLS", "false"));
        if (port <= 0 || username.isBlank() != password.isBlank()) {
            throw new IllegalArgumentException(
                    "SMTP_PORT deve ser positivo e SMTP_USERNAME/PASSWORD devem ser informados juntos");
        }
    }

    public void send(Reminder reminder) throws MessagingException {
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.host", host);
        properties.setProperty("mail.smtp.port", Integer.toString(port));
        properties.setProperty("mail.smtp.auth", Boolean.toString(!username.isBlank()));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(startTls));
        properties.setProperty("mail.smtp.connectiontimeout", "20000");
        properties.setProperty("mail.smtp.timeout", "20000");
        properties.setProperty("mail.smtp.writetimeout", "20000");

        Authenticator authenticator = username.isBlank() ? null : new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        };
        Session session = Session.getInstance(properties, authenticator);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(sender));
        try {
            message.setRecipient(
                    Message.RecipientType.TO,
                    new InternetAddress(
                            reminder.studentEmail(),
                            reminder.studentName(),
                            StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException exception) {
            throw new MessagingException("UTF-8 is unavailable for email headers", exception);
        }
        message.setSubject("Lembrete: " + reminder.assignmentName(), StandardCharsets.UTF_8.name());
        OffsetDateTime dueAt = OffsetDateTime.parse(reminder.dueAt())
                .withOffsetSameInstant(ZoneOffset.UTC);
        message.setContent(
                "Olá, " + (reminder.studentName().isBlank() ? "estudante" : reminder.studentName())
                        + "!\n\nA tarefa " + reminder.assignmentName() + " da disciplina "
                        + reminder.courseName() + " ainda está pendente e vence em "
                        + DEADLINE_FORMAT.format(dueAt) + ".\n\nAcesse a tarefa: "
                        + reminder.assignmentUrl() + "\n",
                "text/plain; charset=UTF-8");
        Transport.send(message);
    }
}
