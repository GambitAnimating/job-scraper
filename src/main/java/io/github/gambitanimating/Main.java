package io.github.gambitanimating;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        String sourceSite = GreenhouseSource.SOURCE;
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        JobRepository jobRepository = new JobRepository(url, user, password);

        JobSource jobSource = new GreenhouseSource("stripe");
        LocalDateTime startTime = LocalDateTime.now();

        try {
            List<Job> jobs = jobSource.fetch();
            int jobsFound = jobs.size();
            int newJobsFound = jobRepository.save(sourceSite, jobs);
            jobRepository.recordRun(sourceSite, startTime, LocalDateTime.now(), jobsFound, newJobsFound, "SUCCESS");

            System.out.println(jobsFound + " jobs found. " + newJobsFound + " were new.");
        } catch (Exception e) {
            e.printStackTrace();
            try {
                jobRepository.recordRun(sourceSite, startTime, LocalDateTime.now(), 0, 0, "FAILED");
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
