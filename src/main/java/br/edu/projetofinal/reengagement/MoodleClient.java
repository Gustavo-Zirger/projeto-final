package br.edu.projetofinal.reengagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MoodleClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final URI restUri;
    private final String token;
    private final HttpClient httpClient;

    public MoodleClient(String restUrl, String token) {
        this.restUri = URI.create(restUrl);
        this.token = token;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public JsonNode getAssignments(long courseId) throws IOException, InterruptedException {
        return call("mod_assign_get_assignments", Map.of("courseids[0]", courseId));
    }

    public JsonNode getEnrolledUsers(long courseId) throws IOException, InterruptedException {
        return call("core_enrol_get_enrolled_users", Map.of("courseid", courseId));
    }

    public JsonNode getSubmissions(List<Long> assignmentIds)
            throws IOException, InterruptedException {
        if (assignmentIds.isEmpty()) {
            return MAPPER.createObjectNode().set("assignments", MAPPER.createArrayNode());
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (int index = 0; index < assignmentIds.size(); index++) {
            parameters.put("assignmentids[" + index + "]", assignmentIds.get(index));
        }
        return call("mod_assign_get_submissions", parameters);
    }

    private JsonNode call(String function, Map<String, ?> parameters)
            throws IOException, InterruptedException {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("wstoken", token);
        form.put("wsfunction", function);
        form.put("moodlewsrestformat", "json");
        form.putAll(parameters);
        List<String> fields = new ArrayList<>();
        form.forEach((key, value) -> fields.add(
                encode(key) + "=" + encode(String.valueOf(value))));

        HttpRequest request = HttpRequest.newBuilder(restUri)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(String.join("&", fields)))
                .build();
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Moodle REST " + function + " returned HTTP "
                    + response.statusCode());
        }

        JsonNode result;
        try {
            result = MAPPER.readTree(response.body());
        } catch (IOException exception) {
            throw new IOException("Moodle REST " + function + " returned invalid JSON", exception);
        }
        if (result != null && result.hasNonNull("exception")) {
            throw new IOException("Moodle REST " + function + ": "
                    + result.path("message").asText("Moodle API error"));
        }
        return result;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
