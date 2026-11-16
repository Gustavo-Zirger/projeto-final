package br.edu.projetofinal.reengagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.io.IOException;

public final class ReminderCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private ReminderCodec() {
    }

    public static byte[] encode(Reminder reminder) throws IOException {
        return MAPPER.writeValueAsBytes(reminder);
    }

    public static Reminder decode(byte[] body) throws IOException {
        try {
            return MAPPER.readValue(body, Reminder.class);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IOException("Invalid reminder message", exception);
        }
    }
}
