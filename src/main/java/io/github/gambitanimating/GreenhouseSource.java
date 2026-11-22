package io.github.gambitanimating;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Reads a company's public Greenhouse job board: one JSON request, no pagination. */
public class GreenhouseSource implements JobSource {

    static final String BASE_URL = "https://boards-api.greenhouse.io/v1/boards/";
    static final String SOURCE = "greenhouse";

    private final String boardToken;
    private final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient client = HttpClient.newHttpClient();

    public GreenhouseSource(String boardToken) {
        this.boardToken = boardToken;
    }

    @Override
    public List<Job> fetch() throws IOException, InterruptedException {
        URI uri = URI.create(BASE_URL + boardToken + "/jobs");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Accept", "application/json")
                .header("User-Agent", "job-scraper/1.0 (+https://github.com/GambitAnimating/job-scraper)")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();

        if (statusCode != 200) {
            throw new IOException("Job fetch unsuccessful " + statusCode);
        }

        return parse(response.body());
    }

    /** Separate from fetch() so a test can feed it a saved JSON file instead of the live site. */
    List<Job> parse(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        List<Job> jobs = new ArrayList<>();
        for (JsonNode node : root.path("jobs")) {
            jobs.add(new Job(
                    text(node, "company_name"),
                    SOURCE,
                    text(node, "id"),
                    text(node, "title"),
                    text(node.path("location"), "name"),
                    text(node, "absolute_url"),
                    date(text(node, "first_published"))));
        }
        return jobs;
    }

    /** The field's value as text, or null if it's missing or JSON null. */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /** "2026-09-09T10:50:29-04:00" -> 2026-09-09, or null if there's no date. */
    private static LocalDate date(String timestamp) {
        try {
            return timestamp == null ? null : OffsetDateTime.parse(timestamp).toLocalDate();
        } catch (DateTimeParseException e) {
            System.err.println("Error parsing date " + timestamp);
            return null;
        }
    }
}
