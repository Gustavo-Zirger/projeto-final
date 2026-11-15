package br.edu.projetofinal.reengagement;

import java.time.OffsetDateTime;

public record Reminder(
        long courseId,
        String courseName,
        long assignmentId,
        String assignmentName,
        long studentId,
        String studentName,
        String studentEmail,
        String dueAt,
        String assignmentUrl) {

    public Reminder {
        if (courseId <= 0 || assignmentId <= 0 || studentId <= 0
                || courseName == null || assignmentName == null || assignmentName.isBlank()
                || studentName == null || studentEmail == null || studentEmail.isBlank()
                || dueAt == null || assignmentUrl == null
                || !assignmentUrl.startsWith("http://") && !assignmentUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Invalid reminder fields");
        }
        OffsetDateTime.parse(dueAt);
    }

    public String eventId() {
        return courseId + ":" + assignmentId + ":" + studentId;
    }
}
