package br.edu.projetofinal.reengagement;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderCodecTest {
    @Test
    void serializesSharedSnakeCaseContract() throws Exception {
        Reminder reminder = new Reminder(
                1, "Curso", 2, "Tarefa", 3, "Aluna", "aluna@example.test",
                "2026-11-15T12:00:00Z", "https://moodle.test/mod/assign/view.php?id=4");

        String json = new String(ReminderCodec.encode(reminder), StandardCharsets.UTF_8);
        Reminder decoded = ReminderCodec.decode(json.getBytes(StandardCharsets.UTF_8));

        assertEquals(reminder, decoded);
        assertTrue(json.contains("\"student_email\""));
    }

    @Test
    void rejectsMalformedMessages() {
        assertThrows(
                java.io.IOException.class,
                () -> ReminderCodec.decode("{\"student_id\":\"not-an-id\"}".getBytes()));
    }
}
