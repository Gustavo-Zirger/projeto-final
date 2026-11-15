package br.edu.projetofinal.reengagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackerServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    private JsonNode courses;
    private JsonNode users;
    private JsonNode submissions;

    @BeforeEach
    void setUp() throws Exception {
        courses = MAPPER.readTree("""
                {"courses":[{"id":7,"fullname":"Engenharia de Software","assignments":[
                  {"id":10,"cmid":99,"name":"Atividade","duedate":1794787200,
                   "teamsubmission":0,"visible":1}
                ]}]}
                """);
        users = MAPPER.readTree("""
                [
                  {"id":4,"fullname":"Estudante","email":"aluno@example.test",
                   "roles":[{"shortname":"student"}]},
                  {"id":5,"fullname":"Professor","email":"prof@example.test",
                   "roles":[{"shortname":"editingteacher"}]}
                ]
                """);
        submissions = MAPPER.readTree(
                "{\"assignments\":[{\"assignmentid\":10,\"submissions\":[]}]}");
    }

    @Test
    void selectsPendingStudentsWithinTheNext24Hours() {
        List<Reminder> reminders = TrackerService.findReminders(
                7, courses, users, submissions, "https://moodle.test", NOW);

        assertEquals(1, reminders.size());
        assertEquals(4, reminders.getFirst().studentId());
        assertEquals(
                "https://moodle.test/mod/assign/view.php?id=99",
                reminders.getFirst().assignmentUrl());
    }

    @Test
    void excludesSubmittedHiddenGroupAndOutOfWindowAssignments() throws Exception {
        JsonNode assignments = courses.path("courses").get(0).path("assignments");
        ((com.fasterxml.jackson.databind.node.ObjectNode) assignments.get(0))
                .put("duedate", NOW.plus(25, ChronoUnit.HOURS).getEpochSecond());
        ((com.fasterxml.jackson.databind.node.ObjectNode) assignments.get(0))
                .put("id", 10);
        ((com.fasterxml.jackson.databind.node.ObjectNode) submissions
                .path("assignments").get(0))
                .set("submissions", MAPPER.readTree("[{\"userid\":4,\"status\":\"submitted\"}]"));
        ((com.fasterxml.jackson.databind.node.ArrayNode) assignments).add(MAPPER.readTree("""
                {"id":11,"cmid":100,"name":"Grupo",
                 "duedate":1794784000,"teamsubmission":1}
                """));
        ((com.fasterxml.jackson.databind.node.ArrayNode) assignments).add(MAPPER.readTree("""
                {"id":12,"cmid":101,"name":"Oculta",
                 "duedate":1794784000,"visible":0}
                """));

        assertEquals(0, TrackerService.findReminders(
                7, courses, users, submissions, "https://moodle.test", NOW).size());
    }

    @Test
    void includesDeadlineExactly24HoursAway() throws Exception {
        ((com.fasterxml.jackson.databind.node.ObjectNode) courses
                .path("courses").get(0).path("assignments").get(0))
                .put("duedate", NOW.plus(24, ChronoUnit.HOURS).getEpochSecond());

        assertEquals(1, TrackerService.findReminders(
                7, courses, users, submissions, "https://moodle.test", NOW).size());
    }

    @Test
    void ignoresOverdueAndUndatedAssignments() throws Exception {
        ((com.fasterxml.jackson.databind.node.ObjectNode) courses
                .path("courses").get(0))
                .set("assignments", MAPPER.readTree("""
                        [
                          {"id":13,"cmid":102,"name":"Vencida",
                           "duedate":1794743940},
                          {"id":14,"cmid":103,"name":"Sem prazo","duedate":null}
                        ]
                        """));

        assertEquals(0, TrackerService.findReminders(
                7, courses, users, submissions, "https://moodle.test", NOW).size());
    }
}
