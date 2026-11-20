package io.github.gambitanimating;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** All database access: upserts jobs with MERGE and records each scrape run. */
public class JobRepository {

    // Insert the company if it's new; nothing to update if it exists.
    private static final String MERGE_COMPANY = """
            MERGE Company AS Target
            USING (SELECT ? AS Name) AS Source
            ON Target.Name = Source.Name
            WHEN NOT MATCHED THEN
                INSERT (Name) VALUES (Source.Name);
            """;

    // Insert new jobs; for known ones refresh the feed's fields and LastSeen.
    // FirstSeen is only set on insert. CompanyId is looked up from the company name.
    private static final String MERGE_JOB = """
            MERGE Job AS Target
            USING (SELECT ? AS SourceSite, ? AS ExternalId, ? AS Title, ? AS Location,
                          ? AS Url, ? AS PostedDate,
                          (SELECT Id FROM Company WHERE Name = ?) AS CompanyId) AS Source
            ON Target.SourceSite = Source.SourceSite AND Target.ExternalId = Source.ExternalId
            WHEN MATCHED THEN
                UPDATE SET Target.Title = Source.Title, Target.Location = Source.Location,
                           Target.Url = Source.Url, Target.PostedDate = Source.PostedDate,
                           Target.LastSeen = SYSDATETIME()
            WHEN NOT MATCHED THEN
                INSERT (CompanyId, SourceSite, ExternalId, Title, Location, Url, PostedDate, FirstSeen, LastSeen)
                VALUES (Source.CompanyId, Source.SourceSite, Source.ExternalId, Source.Title,
                        Source.Location, Source.Url, Source.PostedDate, SYSDATETIME(), SYSDATETIME());
            """;

    private static final String COUNT_JOBS = "SELECT COUNT(*) FROM Job WHERE SourceSite = ?";

    private static final String INSERT_RUN = """
            INSERT INTO ScrapeRun (SourceSite, StartTime, EndTime, JobsFound, JobsNew, Status)
            VALUES (?, ?, ?, ?, ?, ?);
            """;

    private final String url;
    private final String user;
    private final String password;

    public JobRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Upserts all jobs from one source in a single transaction. Returns how many were new. */
    public int save(String sourceSite, List<Job> jobs) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            conn.setAutoCommit(false); // all of this source's jobs, or none
            try {
                int before = countJobs(conn, sourceSite);

                try (PreparedStatement company = conn.prepareStatement(MERGE_COMPANY);
                     PreparedStatement job = conn.prepareStatement(MERGE_JOB)) {

                    // Companies first, because each job needs its company's Id.
                    for (String name : jobs.stream().map(Job::companyName).distinct().toList()) {
                        company.setString(1, name);
                        company.addBatch();
                    }
                    company.executeBatch();

                    for (Job j : jobs) {
                        job.setString(1, j.sourceSite());
                        job.setString(2, j.externalId());
                        job.setString(3, j.title());
                        job.setString(4, j.location());
                        job.setString(5, j.url());
                        job.setDate(6, j.postedDate() == null ? null : Date.valueOf(j.postedDate()));
                        job.setString(7, j.companyName());
                        job.addBatch();
                    }
                    job.executeBatch(); // one round trip per batch, not one per job
                }

                int after = countJobs(conn, sourceSite);
                conn.commit();
                return after - before;
            } catch (SQLException e) {
                conn.rollback(); // undo the partial load, then let the caller know
                throw e;
            }
        }
    }

    /** Records one run of one source in ScrapeRun. */
    public void recordRun(String sourceSite, LocalDateTime start, LocalDateTime end,
                          int jobsFound, int jobsNew, String status) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement stmt = conn.prepareStatement(INSERT_RUN)) {
            stmt.setString(1, sourceSite);
            stmt.setTimestamp(2, Timestamp.valueOf(start));
            stmt.setTimestamp(3, Timestamp.valueOf(end));
            stmt.setInt(4, jobsFound);
            stmt.setInt(5, jobsNew);
            stmt.setString(6, status);
            stmt.executeUpdate();
        }
    }

    private static int countJobs(Connection conn, String sourceSite) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(COUNT_JOBS)) {
            stmt.setString(1, sourceSite);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
