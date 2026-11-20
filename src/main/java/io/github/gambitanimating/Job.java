package io.github.gambitanimating;

import java.time.LocalDate;

public record Job(String companyName, String sourceSite, String externalId, String title, String location, String url, LocalDate postedDate) {
}
