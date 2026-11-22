package io.github.gambitanimating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Parses a saved sample of the Greenhouse feed, not the live site. */
class GreenhouseSourceTest {
    private List<Job> parseSample() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/greenhouse-sample.json")) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new GreenhouseSource("stripe").parse(json);
        }
    }

    @Test
    void mapsAllFieldsOfACompleteJob() throws IOException {
        Job job = parseSample().get(0);

        assertEquals("Stripe", job.companyName());
        assertEquals("greenhouse", job.sourceSite());
        assertEquals("7012345", job.externalId());
        assertEquals("Backend Engineer", job.title());
        assertEquals("Remote, US", job.location());
        assertEquals("https://stripe.com/jobs/search?gh_jid=7012345", job.url());
        assertEquals(LocalDate.of(2026, 9, 9), job.postedDate());
    }

    @Test
    void missingOrNullFieldsBecomeNullInsteadOfCrashing() throws IOException {
        List<Job> jobs = parseSample();
        Job job = jobs.get(1); // no "location" key, "first_published": null

        assertEquals(3, jobs.size());
        assertNull(job.location());
        assertNull(job.postedDate());
        assertEquals("Data Engineer", job.title());
    }

    @Test
    void invalidDatesAreHandled() throws IOException {
        List<Job> jobs = parseSample();
        Job job = jobs.get(2); // "first_published": "yesterday", not a date

        assertEquals("C# Engineer", job.title());
        assertNull(job.postedDate());
    }
}
