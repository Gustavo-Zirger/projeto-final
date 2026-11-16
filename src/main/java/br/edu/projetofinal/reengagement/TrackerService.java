package br.edu.projetofinal.reengagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TrackerService {
    private static final Logger LOGGER = Logger.getLogger(TrackerService.class.getName());
    private static final Duration WINDOW = Duration.ofHours(24);

    public void run() throws InterruptedException {
        String token = requiredEnv("MOODLE_TOKEN");
        long courseId = Long.parseLong(requiredEnv("MOODLE_COURSE_ID"));
        long intervalSeconds = Long.parseLong(
                System.getenv().getOrDefault("POLL_INTERVAL_SECONDS", "3600"));
        if (courseId <= 0 || intervalSeconds <= 0) {
            throw new IllegalArgumentException(
                    "MOODLE_COURSE_ID e POLL_INTERVAL_SECONDS devem ser positivos");
        }
        MoodleClient moodle = new MoodleClient(
                System.getenv().getOrDefault(
                        "MOODLE_REST_URL",
                        "http://aplicacao_web:8080/webservice/rest/server.php"),
                token);
        String publicUrl = System.getenv().getOrDefault(
                "MOODLE_PUBLIC_URL", "http://localhost");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                int count = scan(moodle, courseId, publicUrl);
                LOGGER.info("Varredura concluida; " + count + " lembrete(s) publicado(s)");
            } catch (Exception exception) {
                LOGGER.log(Level.SEVERE, "Falha na varredura do Moodle", exception);
            }
            Thread.sleep(Duration.ofSeconds(intervalSeconds).toMillis());
        }
    }

    static List<Reminder> findReminders(
            long courseId,
            JsonNode coursesResponse,
            JsonNode usersResponse,
            JsonNode submissionsResponse,
            String publicUrl,
            Instant now) {
        JsonNode courses = requireArray(coursesResponse.path("courses"), "courses");
        JsonNode course = null;
        for (JsonNode candidate : courses) {
            if (requiredLong(candidate, "id") == courseId) {
                course = candidate;
                break;
            }
        }
        if (course == null) {
            throw new IllegalArgumentException("Curso " + courseId + " nao retornado pelo Moodle");
        }

        JsonNode assignments = requireArray(course.path("assignments"), "assignments");
        JsonNode users = requireArray(usersResponse, "enrolled users");
        JsonNode submissionAssignments = requireArray(
                submissionsResponse.path("assignments"), "submissions.assignments");
        Map<Long, Set<Long>> submittedByAssignment = new HashMap<>();
        for (JsonNode submissionAssignment : submissionAssignments) {
            long assignmentId = requiredLong(submissionAssignment, "assignmentid");
            JsonNode submissions = submissionAssignment.path("submissions");
            if (submissions.isMissingNode()) {
                submissions = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                        .arrayNode();
            }
            requireArray(submissions, "submissions");
            Set<Long> submitted = new HashSet<>();
            for (JsonNode submission : submissions) {
                if ("submitted".equals(submission.path("status").asText())) {
                    submitted.add(requiredLong(submission, "userid"));
                }
            }
            submittedByAssignment.put(assignmentId, submitted);
        }

        Instant deadline = now.plus(WINDOW);
        List<Reminder> reminders = new ArrayList<>();
        for (JsonNode assignment : assignments) {
            if (isZeroOrFalse(assignment.path("visible"))
                    || isOneOrTrue(assignment.path("teamsubmission"))) {
                continue;
            }
            long assignmentId = requiredLong(assignment, "id");
            JsonNode dueNode = assignment.path("duedate");
            if (dueNode.isMissingNode() || dueNode.isNull()
                    || dueNode.asText().isBlank() || "0".equals(dueNode.asText())) {
                continue;
            }
            long dueTimestamp = requiredLong(assignment, "duedate");
            if (dueTimestamp <= 0) {
                continue;
            }
            Instant dueAt = Instant.ofEpochSecond(dueTimestamp);
            if (!dueAt.isAfter(now) || dueAt.isAfter(deadline)) {
                continue;
            }

            long cmid = requiredLong(assignment, "cmid");
            Set<Long> submitted = submittedByAssignment.getOrDefault(
                    assignmentId, Set.of());
            for (JsonNode user : users) {
                if (!isStudent(user.path("roles"))) {
                    continue;
                }
                long studentId = requiredLong(user, "id");
                String email = user.path("email").asText("").trim();
                if (submitted.contains(studentId) || email.isEmpty()) {
                    continue;
                }
                reminders.add(new Reminder(
                        courseId,
                        course.path("fullname").asText(""),
                        assignmentId,
                        assignment.path("name").asText(""),
                        studentId,
                        user.path("fullname").asText(""),
                        email,
                        dueAt.atOffset(ZoneOffset.UTC).toString(),
                        publicUrl.replaceAll("/+$", "")
                                + "/mod/assign/view.php?id=" + cmid));
            }
        }
        return reminders;
    }

    private int scan(MoodleClient moodle, long courseId, String publicUrl) throws Exception {
        JsonNode assignments = moodle.getAssignments(courseId);
        JsonNode users = moodle.getEnrolledUsers(courseId);
        JsonNode course = null;
        for (JsonNode candidate : requireArray(assignments.path("courses"), "courses")) {
            if (requiredLong(candidate, "id") == courseId) {
                course = candidate;
                break;
            }
        }
        if (course == null) {
            throw new IllegalArgumentException("Curso " + courseId + " nao retornado pelo Moodle");
        }

        List<Long> assignmentIds = new ArrayList<>();
        for (JsonNode assignment : requireArray(course.path("assignments"), "assignments")) {
            assignmentIds.add(requiredLong(assignment, "id"));
        }
        JsonNode submissions = moodle.getSubmissions(assignmentIds);
        List<Reminder> reminders = findReminders(
                courseId, assignments, users, submissions, publicUrl, Instant.now());
        publish(reminders);
        return reminders.size();
    }

    private void publish(List<Reminder> reminders) throws Exception {
        if (reminders.isEmpty()) {
            return;
        }
        try (Connection connection = RabbitConnections.connect();
             Channel channel = connection.createChannel()) {
            declareMainQueue(channel);
            channel.confirmSelect();
            for (Reminder reminder : reminders) {
                channel.basicPublish(
                        "", RabbitConnections.QUEUE,
                        new AMQP.BasicProperties.Builder()
                                .deliveryMode(2)
                                .contentType("application/json")
                                .build(),
                        ReminderCodec.encode(reminder));
            }
            channel.waitForConfirmsOrDie();
        }
    }

    static JsonNode requireArray(JsonNode node, String name) {
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException("Resposta do Moodle invalida: "
                    + name + " deve ser uma lista");
        }
        return node;
    }

    static long requiredLong(JsonNode object, String field) {
        JsonNode value = object.path(field);
        if (!value.canConvertToLong()) {
            throw new IllegalArgumentException("Resposta do Moodle invalida: "
                    + field + " deve ser um inteiro");
        }
        return value.longValue();
    }

    private static boolean isStudent(JsonNode roles) {
        if (!roles.isArray()) {
            return false;
        }
        for (JsonNode role : roles) {
            if ("student".equals(role.path("shortname").asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isZeroOrFalse(JsonNode value) {
        return value.isBoolean() && !value.booleanValue()
                || value.canConvertToInt() && value.intValue() == 0
                || value.isTextual() && "0".equals(value.textValue());
    }

    private static boolean isOneOrTrue(JsonNode value) {
        return value.isBoolean() && value.booleanValue()
                || value.canConvertToInt() && value.intValue() == 1
                || value.isTextual() && "1".equals(value.textValue());
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Configure " + name);
        }
        return value.trim();
    }

    private static void declareMainQueue(Channel channel) throws IOException {
        channel.queueDeclare(
                RabbitConnections.QUEUE, true, false, false,
                Map.of(
                        "x-dead-letter-exchange", "",
                        "x-dead-letter-routing-key", RabbitConnections.FAILED_QUEUE));
    }
}
