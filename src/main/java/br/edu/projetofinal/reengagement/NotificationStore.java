package br.edu.projetofinal.reengagement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

public final class NotificationStore {
    private final String jdbcUrl;

    public NotificationStore(String path) throws Exception {
        if (!":memory:".equals(path)) {
            Path database = Path.of(path);
            Path parent = database.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        }
        this.jdbcUrl = "jdbc:sqlite:" + path;
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS notification_results (
                        event_id TEXT PRIMARY KEY,
                        course_id INTEGER NOT NULL,
                        assignment_id INTEGER NOT NULL,
                        student_id INTEGER NOT NULL,
                        student_email TEXT NOT NULL,
                        status TEXT NOT NULL,
                        attempts INTEGER NOT NULL DEFAULT 0,
                        last_error TEXT,
                        updated_at TEXT NOT NULL,
                        sent_at TEXT
                    )
                    """);
        }
    }

    public boolean beginAttempt(Reminder reminder) throws SQLException {
        String now = Instant.now().toString();
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO notification_results
                        (event_id, course_id, assignment_id, student_id, student_email,
                         status, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'pending', ?)
                    ON CONFLICT(event_id) DO NOTHING
                    """)) {
                insert.setString(1, reminder.eventId());
                insert.setLong(2, reminder.courseId());
                insert.setLong(3, reminder.assignmentId());
                insert.setLong(4, reminder.studentId());
                insert.setString(5, reminder.studentEmail());
                insert.setString(6, now);
                insert.executeUpdate();
            }

            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT status FROM notification_results WHERE event_id = ?")) {
                query.setString(1, reminder.eventId());
                try (ResultSet result = query.executeQuery()) {
                    if (result.next() && "sent".equals(result.getString("status"))) {
                        connection.rollback();
                        return false;
                    }
                }
            }

            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE notification_results
                    SET status = 'processing', attempts = attempts + 1,
                        last_error = NULL, updated_at = ?
                    WHERE event_id = ?
                    """)) {
                update.setString(1, now);
                update.setString(2, reminder.eventId());
                update.executeUpdate();
            }
            connection.commit();
            return true;
        }
    }

    public void markSent(Reminder reminder) throws SQLException {
        String now = Instant.now().toString();
        try (Connection connection = connect();
             PreparedStatement update = connection.prepareStatement("""
                     UPDATE notification_results
                     SET status = 'sent', last_error = NULL, updated_at = ?, sent_at = ?
                     WHERE event_id = ?
                     """)) {
            update.setString(1, now);
            update.setString(2, now);
            update.setString(3, reminder.eventId());
            update.executeUpdate();
        }
    }

    public void markFailed(Reminder reminder, Exception error) throws SQLException {
        String now = Instant.now().toString();
        try (Connection connection = connect();
             PreparedStatement update = connection.prepareStatement("""
                     UPDATE notification_results
                     SET status = 'failed', last_error = ?, updated_at = ?
                     WHERE event_id = ?
                     """)) {
            update.setString(1, String.valueOf(error.getMessage()).substring(
                    0, Math.min(2000, String.valueOf(error.getMessage()).length())));
            update.setString(2, now);
            update.setString(3, reminder.eventId());
            update.executeUpdate();
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 30000");
        }
        return connection;
    }
}
