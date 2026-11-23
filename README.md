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
- An HTML source (jsoup and CSS selectors); an XML feed source after that
- Report queries (jobs per company, new this run, no longer seen) and an index
  tuned with the execution plan
- Validating required fields before saving (see the failure table)

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
  non-200 response is an error, requests time out after 10 seconds, and any
  exception records a `FAILED` run, except when the database itself is down
  (see the table below).
- **Least privilege.** The app's database login has read and write roles only;
  the schema is created by an admin.
- **Polite scraping.** Only public sources whose robots.txt and terms allow
  it, with a descriptive `User-Agent` that links back here.
- **Official API before HTML.** For Stripe I used Greenhouse's public job
  board API instead of scraping the careers page: structured JSON is far less
  likely to break than page markup, and it's what the site offers for this.

## What I expect to fail, and how I'd handle it

| Failure | What happens today | What I'd do next |
| --- | --- | --- |
| The source changes its JSON shape (e.g. renames `title`) | The missing field becomes `null`. Because `Title` is `NOT NULL`, SQL Server rejects the row, the whole batch rolls back (losing the good jobs too), and the run is recorded as `FAILED`. | Validate the required fields in `Main` before saving: skip and log jobs missing a required value, and fail that source's run if more than a set share (say 5–30%) are bad, since that means the parser is broken, not one bad record. |
| One malformed date (e.g. `"yesterday"`) | The date becomes `null`, which `PostedDate` allows, so the job is still saved; the bad value is printed to the console. (Tested in `GreenhouseSourceTest`.) | Log to a file instead of the console, and count bad dates per run, so a format change that breaks every date gets noticed instead of quietly saving nulls. |
| The site is down, slow or rate-limits us | A non-200 status (500, 429) throws an `IOException`. If the site connects but never answers, the 10-second request timeout throws `HttpTimeoutException`. Either way `Main` records the run as `FAILED`, and nothing is saved. | Retry a few times with growing waits (exponential backoff) for 500s and timeouts. On 429, wait at least the `Retry-After` time, plus a little padding, before retrying. After the last retry, fail the run. |
| The database is unreachable (down, or wrong credentials) | `save()` throws `SQLException`, and `Main` tries to record `FAILED`, but that needs the database too, so it throws again and the program stops with a stack trace on the console. No `ScrapeRun` row exists for that run. | Log to a file as well as the database, so a failed run always leaves a record, and alert on the program's error exit code (e.g. from the scheduler). |
| Two runs at once | Both `MERGE`s can see the same new job as missing and both try to insert it. The unique constraint on `(SourceSite, ExternalId)` rejects the second, so no duplicate is stored, but that run rolls back and is recorded as `FAILED`. | Add `HOLDLOCK` to both `MERGE`s, so the second run waits and then updates instead of failing (this also covers two sources inserting the same company). Also have the scheduler allow one run at a time, as a second safety net. |
| A job is taken down | It isn't deleted; its `LastSeen` stops updating. That's deliberate: if it's reposted with the same ID, the `MERGE` just refreshes it, and "no longer seen" is a report query instead of lost data. | Mark jobs not seen in the latest run as closed (e.g. an `IsActive` column), so open and closed jobs are easy to tell apart, while keeping the history. |
| Much more data (millions of jobs) | Each job is its own `MERGE` statement. Batching saves network round trips, but SQL Server still runs a million small statements, each with its own overhead. | Bulk-load all the rows into a staging table (an empty table with the same columns as `Job`), then run one set-based `MERGE` from it into `Job`. The overhead is paid once, not per job. |

## Security

- **SQL injection:** everything we scrape is untrusted, since any site could put
  SQL in a job title. An attacker can't inject SQL, because every value goes
  through a `?` placeholder instead of string concatenation, so the value is
  never parsed as SQL.
- **Credentials:** the database password lives in Windows environment variables
  (set with `setx`), and the code reads them with `System.getenv`. It isn't in the
  code, because anyone with the compiled class could decompile it and it would
  stay in git history forever. It isn't in IntelliJ's run configuration either,
  because those files can be committed by accident. A git-ignored `.env` file is a
  common alternative.
- **Least privilege:** the app's database login can read and write rows in the
  existing tables, but can't create or drop tables; only an admin can. If the code
  has a bug, it can't accidentally drop our tables. If the password leaked, an
  attacker couldn't drop tables or create new ones, but could still delete all the
  company, job and run data, which is what backups are for.
- **Scraped text on a web page:** if a job title or any other scraped text is ever
  shown on a job board, it must be HTML-escaped. Otherwise an attacker could put a
  script in a title, and it would run for every visitor (cross-site scripting).
  Escaping shows the special symbols as text instead of treating them as markup.
- **Transport:** the fetch from Greenhouse uses HTTPS, and the database connection
  is encrypted with `encrypt=true`, so people on the same network can't read
  what's being sent. `trustServerCertificate=true` skips checking the server's
  certificate. Without that check you can't be sure who's on the other end:
  somebody could act as the server and read everything (a man-in-the-middle
  attack). Locally that's fine, because my SQL Server uses a self-signed
  certificate and the connection never leaves my machine. In production the
  setting would be off and the server would have a certificate from a trusted
  authority.

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
