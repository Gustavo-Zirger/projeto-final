package br.edu.projetofinal.reengagement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationStoreTest {
    @TempDir
    Path tempDir;

    private NotificationStore store;
    private Reminder reminder;

    @BeforeEach
    void setUp() throws Exception {
        store = new NotificationStore(tempDir.resolve("results.sqlite3").toString());
        reminder = new Reminder(
                1,
                "Curso",
                2,
                "Tarefa",
                3,
                "Aluna",
                "aluna@example.test",
                "2026-11-15T12:00:00Z",
                "https://moodle.test/mod/assign/view.php?id=4");
    }

    @Test
    void sentReminderIsIdempotent() throws Exception {
        assertTrue(store.beginAttempt(reminder));
        store.markSent(reminder);

        assertFalse(store.beginAttempt(reminder));
    }

    @Test
    void failedReminderCanBeRetried() throws Exception {
        assertTrue(store.beginAttempt(reminder));
        store.markFailed(reminder, new IllegalStateException("SMTP indisponivel"));

        assertTrue(store.beginAttempt(reminder));
    }
}
