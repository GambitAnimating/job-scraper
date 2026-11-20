package io.github.gambitanimating;

import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        JobSource jobSource = new GreenhouseSource("stripe");
        try {
            List<Job> jobs = jobSource.fetch();
            System.out.println("Jobs Count: " + jobs.size());
            for (Job job : jobs) {
                System.out.println(job.title());
            }
        }
        catch (IOException | InterruptedException e) {
            System.err.println(e);
        }
    }
}
