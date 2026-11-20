# job-scraper

A Java 21 program that pulls job postings from public sources and loads them
into SQL Server. Re-running it is safe: known jobs are updated in place, new
ones are inserted, and every run is logged.

I built this to learn Java, JDBC and SQL Server by doing a small version of a
job-scraping pipeline. My background is five years of professional C#.

## Status

**Working now**
- **JSON source:** Stripe's public Greenhouse job board API
  (`boards-api.greenhouse.io/v1/boards/stripe/jobs`), about 775 jobs per run,
  mapped to a `Job` record with Jackson. Missing or null fields become `null`
  instead of crashing.
- **Idempotent load:** jobs are upserted with a T-SQL `MERGE` keyed on source
  and external ID. Verified: the first run inserted 775 jobs; the second run
  inserted 0 and only refreshed `LastSeen`.
- **Run log:** each run writes a `ScrapeRun` row with start and end time, jobs
  found, jobs new, and `SUCCESS` or `FAILED`.

**In progress**
- An HTML source (jsoup and CSS selectors) and an XML feed source
- Report queries (jobs per company, new this run, no longer seen) and an index
  tuned with the execution plan
- Unit tests for each parser against saved sample files

## How it works

| Piece | Role |
| --- | --- |
| `Job` | Record: company, source, external ID, title, location, URL, posted date |
| `JobSource` | Interface with one method, `List<Job> fetch()` |
| `GreenhouseSource` | Fetches with `HttpClient`, checks the status code, parses with Jackson |
| `JobRepository` | All JDBC: the `MERGE` upsert and the `ScrapeRun` insert |
| `Main` | Runs the source, saves the jobs, records the run |

Database schema (`sql/schema.sql`): `Company`, `Job` and `ScrapeRun`, with a
foreign key from `Job` to `Company` and a unique constraint on
`(SourceSite, ExternalId)`.

## Design decisions

- **`MERGE` instead of `INSERT`.** A scraper sees the same jobs every run.
  `MERGE` inserts new jobs (setting `FirstSeen`) and updates known ones
  (refreshing the feed's fields and `LastSeen`), so runs never duplicate rows.
  `FirstSeen` and `LastSeen` make "new this run" and "no longer listed" simple
  queries.
- **One transaction per source.** A failure part-way through rolls the whole
  source back, so a run never leaves a half-loaded table, and re-running is
  safe.
- **Batched statements.** The jobs are sent with `addBatch`/`executeBatch`
  rather than one round trip each; a 775-job run takes about a second.
- **Parameterized SQL only.** Every value is passed through `?` parameters, so
  scraped text is always treated as data, never as SQL.
- **Failures aren't silent.** `fetch()` declares its checked exceptions, a
  non-200 response is an error, and failed runs are recorded as `FAILED`.
- **Least privilege.** The app's database login has read and write roles only;
  the schema is created by an admin.
- **Polite scraping.** Only public sources whose robots.txt and terms allow
  it, with a descriptive `User-Agent` that links back here.

## Running it

Requirements: Java 21, Maven, SQL Server (Developer edition works).

1. Create a database named `jobscraper` and run `sql/schema.sql` in it.
2. Create a SQL login for the app with `db_datareader` and `db_datawriter`.
3. Set the connection details as environment variables (never in the repo):

   ```
   setx DB_URL "jdbc:sqlserver://localhost:1433;databaseName=jobscraper;encrypt=true;trustServerCertificate=true"
   setx DB_USER "<your app login>"
   setx DB_PASSWORD "<its password>"
   ```

4. Build with `mvn package`, then run `io.github.gambitanimating.Main` from
   your IDE. (A runnable jar is on the next-steps list.)

## How I used AI

I worked with Claude Code as a tutor and pair programmer, under rules I wrote
for it. I wrote the schema, the `Job` record, the `JobSource`
interface, the HTTP fetch in `GreenhouseSource`, `Main`, and the `MERGE`
statement (drafted and tested by hand in SSMS). Claude drafted the JSON
parsing in `GreenhouseSource`, the JDBC code in `JobRepository` around my
`MERGE`, and this README, and I reviewed each part. I keep a log of my
prompts and can share it on request.
