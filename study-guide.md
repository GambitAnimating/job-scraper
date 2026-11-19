# Study guide

Interview-style questions from what came up while building this project.
Cover the answer, say it out loud, then check. Claude adds to this file;
`notes.md` is for your own one-line summaries.

## SQL Server

**Q: Why does a fresh SQL Server reject connections until you tick "Trust Server Certificate"? What's the JDBC equivalent?**
<details><summary>Answer</summary>

SQL Server encrypts connections using a certificate (its proof of identity).
A new install makes a self-signed one, so no trusted authority vouches for it
and clients refuse it by default. "Trust Server Certificate" in SSMS, or
`trustServerCertificate=true` in the JDBC connection string, says "accept it
anyway". Fine for a local dev box; in production the server should have a
proper certificate and you leave this off.
</details>

**Q: You installed SQL Server and Java can't connect. Name two default settings to check.**
<details><summary>Answer</summary>

1. **TCP/IP is disabled** on a new Developer install, so nothing listens on
   port 1433. Enable it in SQL Server Configuration Manager, then restart the
   service.
2. **Authentication is Windows-only.** An app logging in with a username and
   password needs "SQL Server and Windows Authentication mode" (mixed mode),
   set in SSMS under server Properties > Security, then a restart.
</details>

**Q: What is T-SQL?**
<details><summary>Answer</summary>

Transact-SQL: Microsoft's dialect of SQL, the language SQL Server runs. It's
standard SQL (`SELECT`, `JOIN`, `CREATE TABLE`...) plus Microsoft extensions:
`TOP`, `IDENTITY`, `NVARCHAR`, variables (`DECLARE @x`), `IF`/`ELSE`,
`TRY`/`CATCH`, and admin statements like `CREATE LOGIN`. Postgres's equivalent
is PL/pgSQL, Oracle's is PL/SQL. Search for syntax with "T-SQL" or "SQL
Server" in the query or you'll get answers for the wrong dialect.
</details>

**Q: SQL Server: what's the difference between a login and a user? What is least privilege?**
<details><summary>Answer</summary>

A login gets you into the server (created once, server-wide). A user gets you
into one database and is linked to a login; a login with no user in a
database can't touch its tables. Least privilege: give each account only what
it needs. The scraper's user gets `db_datareader` and `db_datawriter`, not
`db_owner`, because it only reads and writes rows; tables are created by an
admin running `schema.sql`. A bug or a breach then can't drop tables.
</details>

**Q: What do square brackets mean in T-SQL? Why does `ALTER ROLE [db_datareader, db_datawriter] ADD MEMBER x` fail?**
<details><summary>Answer</summary>

Square brackets quote one identifier (a name), e.g. `[my table]` for a name
with a space. So that statement looks for a single role literally named
`db_datareader, db_datawriter`. `ALTER ROLE` takes one role per statement:
write two statements. Also end T-SQL statements with `;` (`MERGE` requires it).
</details>

**Q: Your app gets "Login failed for user 'x'" from SQL Server. How do you find out why?**
<details><summary>Answer</summary>

The client is deliberately told only "Login failed" so an attacker can't learn
whether the user exists or the password was wrong. The real reason is in the
SQL Server error log (SSMS: Management > SQL Server Logs, or
`EXEC xp_readerrorlog`), e.g. "Password did not match", "login is disabled",
or "cannot open database". Read the log instead of guessing.
</details>

**Q: How is SQL Server different from PostgreSQL?**
<details><summary>Answer</summary>

Same concepts (tables, keys, joins, indexes, transactions); different dialect
and tooling. SQL Server uses T-SQL: `IDENTITY(1,1)` instead of `SERIAL`,
`NVARCHAR` for Unicode text, `DATETIME2`, `TOP n` instead of `LIMIT n`,
`MERGE` for upserts instead of `ON CONFLICT`. GUI is SSMS, not pgAdmin. It's
Microsoft and Windows-first, and supports Windows logins as well as SQL logins.
</details>

**Q: Why Developer edition?**
<details><summary>Answer</summary>

Free, with the full feature set of the paid editions; the licence only forbids
production use. Express is also free but cut down.
</details>

## Java and tooling

**Q: You installed JDK 21 but `java -version` still says 20. Why, and how do you fix it?**
<details><summary>Answer</summary>

A terminal copies PATH when it opens, so one opened before the install still
has the old list. Open a new terminal (or restart the IDE). If it's still
wrong, check PATH order: Windows runs the first `java.exe` it finds, so the
JDK 21 `bin` folder must come before older Java entries.
</details>

**Q: You changed an environment variable with `setx` but your running app still sees the old value. Why?**
<details><summary>Answer</summary>

Each process gets its own copy of the environment when it starts, inherited
from whatever launched it. `setx` saves the new value to the registry and
broadcasts a "settings changed" message, but most programs (IntelliJ,
terminals, any running JVM) ignore it, and Java's `System.getenv()` is a
read-only snapshot. Fully exit and relaunch the program (from the Start menu,
not an old terminal). Like a struct passed by value in C#.
</details>

**Q: Why Java 21 and not the newest release?**
<details><summary>Answer</summary>

21 is a long-term-support (LTS) release, which gets security fixes for years,
and companies standardise on LTS versions. Non-LTS releases like 27 are only
supported for six months. Everything this project needs (records,
`HttpClient`, try-with-resources) is in 21.
</details>

**Q: Name each library in this project and what it's for.**
<details><summary>Answer</summary>

- **jsoup**: parse HTML, select elements with CSS selectors (C#: HtmlAgilityPack/AngleSharp).
- **jackson-databind**: map JSON to Java objects and back (C#: System.Text.Json/Newtonsoft).
- **mssql-jdbc**: Microsoft's JDBC driver, lets Java talk to SQL Server (C#: Microsoft.Data.SqlClient).
- **JUnit 5** (test scope): unit tests (C#: xUnit/NUnit).
- Built into the JDK, no library needed: `java.net.http.HttpClient` for fetching, the built-in XML parser for feeds.

Pipeline: fetch (HttpClient) → parse (Jackson/jsoup/XML) → store (JDBC) → verify (JUnit).
</details>

**Q: What does `pom.xml` do? What's the C# equivalent?**
<details><summary>Answer</summary>

It's the project file Maven builds from, like a `.csproj`. It holds the
project's identity (`groupId`, `artifactId`, `version`), its dependencies
(like `<PackageReference>`, downloaded from Maven Central the way NuGet
downloads packages), the Java version to compile for
(`maven.compiler.release`), and plugin settings.
</details>

**Q: What are `groupId` and `artifactId`?**
<details><summary>Answer</summary>

`groupId` says who owns the project, as a reversed domain
(`io.github.gambitanimating`). `artifactId` is the project's own name
(`job-scraper`). Together with `version` they uniquely identify a library,
which is how dependencies are referenced in a POM.
</details>

**Q: Why does `App.java` live in `src/main/java/io/github/gambitanimating/`?**
<details><summary>Answer</summary>

Maven's fixed layout puts main code in `src/main/java` and tests in
`src/test/java`. Under that, Java requires the folder path to match the
`package` declaration. In C# a namespace is just a label and the file can live
anywhere; in Java a mismatched package and folder is a compile error.
</details>

**Q: What happens when you run `mvn package`?**
<details><summary>Answer</summary>

Maven runs its lifecycle phases in order: compile main code, compile tests,
run the tests (a failing test stops the build), then package the compiled
classes into a `.jar` in `target/` (a zip of classes, like a .NET `.dll`).
`target/` is build output and is never committed.
</details>

**Q: What is the classpath? What does `java -cp target\classes io.github.gambitanimating.App` do?**
<details><summary>Answer</summary>

The classpath is the list of folders and jars the JVM searches for compiled
classes. That command says "look in `target\classes`, and run the `main`
method of the class with this fully qualified name (package plus class)". You
give a class name, not a file path.
</details>

**Q: How do you decide whether a third-party library is safe to add?**
<details><summary>Answer</summary>

Check the group ID matches the official project (lookalike packages exist;
`org.jsoup`, `com.fasterxml.jackson.core`, `com.microsoft.sqlserver`), that
it's actively maintained (recent releases), the licence (MIT and Apache 2.0
are permissive), and known vulnerabilities for the exact version. Don't judge
by Maven Central's "Used in" count: it only counts other published libraries,
not the applications that use it.
</details>

**Q: What is a CVE / CVSS score, and how did it affect your choice of jsoup version?**
<details><summary>Answer</summary>

A CVE is a publicly recorded security flaw in a specific version of some
software; CVSS scores its severity from 0 to 10 (7+ is High, 9+ Critical).
jsoup 1.23.1 and older had a High (8.7) vulnerability fixed in 1.23.2, so I
used the current release, 1.24.1, which has none. Lesson: don't copy version
numbers from old tutorials; pick the latest stable and check it.
</details>

**Q: What was Log4Shell, and why do dependency vulnerabilities matter more for a scraper?**
<details><summary>Answer</summary>

CVE-2021-44228, a CVSS 10.0 flaw in Log4j (a widely used Java logging
library): if an attacker got a crafted string logged, the server would fetch
and run their code. A scraper feeds untrusted input (other people's pages and
feeds) straight into parsers like jsoup and Jackson, so a flaw in a parser is
directly reachable by whoever controls the page.
</details>

**Q: What does HTTP 429 mean, and what should a scraper do about it?**
<details><summary>Answer</summary>

"Too Many Requests": the server is rate-limiting you for asking too often. A
polite scraper avoids it with a delay between requests, and if it gets one
anyway it backs off (waits, often as long as the `Retry-After` header says)
instead of retrying immediately. Hammering through 429s gets you blocked.
</details>

**Q: What's the difference between `<dependencyManagement>` and `<dependencies>` in a POM?**
<details><summary>Answer</summary>

`<dependencies>` actually adds a library to the project. `<dependencyManagement>`
only pins which version to use if something asks for it, and adds nothing on
its own. Like .NET Central Package Management: `<PackageVersion>` sets the
version, `<PackageReference>` pulls it in. A BOM (bill of materials, e.g.
`junit-bom`) imported there supplies a set of versions known to work together,
which is why the JUnit entries need no `<version>`.
</details>

**Q: What does `<scope>test</scope>` on a dependency mean?**
<details><summary>Answer</summary>

The library is only available to test code and isn't part of the running
program. Right for JUnit; wrong for anything the program itself needs (jsoup,
Jackson, the JDBC driver), which would then be missing at runtime.
</details>

**Q: What is try-with-resources and why does JDBC code use it?**
<details><summary>Answer</summary>

`try (Resource r = ...) { }` closes everything opened in the parentheses when
the block ends, even on an exception, in reverse order. It's C#'s `using`.
The name: it's a `try` block "with resources", where a resource is any object
implementing `AutoCloseable` (one method, `close()`), Java's `IDisposable`.
Database connections, statements and result sets hold scarce resources on the
server; leak them and the database runs out of connections and the app hangs.
</details>

**Q: What is a checked exception? How is it different from C#?**
<details><summary>Answer</summary>

Java forces you to either catch certain exceptions (like `SQLException`,
`IOException`) or declare them with `throws` on the method; otherwise it
won't compile. C# has no checked exceptions: any method can throw anything
without declaring it.
</details>

**Q: How does JDBC relate to the `mssql-jdbc` driver? Any gotchas reading a `ResultSet`?**
<details><summary>Answer</summary>

JDBC is the standard API in `java.sql` (`Connection`, `Statement`,
`ResultSet`); the driver is the SQL Server implementation behind it, picked by
the `jdbc:sqlserver:` URL prefix. Like ADO.NET's `DbConnection` and
`SqlConnection`. A `ResultSet` starts before the first row, so call `next()`
first, and columns are numbered from 1, not 0.
</details>

**Q: `git status` lists your whole user folder instead of your project. What happened?**
<details><summary>Answer</summary>

`git init` was run in the wrong directory (the home folder, where a new
terminal starts). Git searches upward through parent folders for a `.git`, so
a repo in a parent folder captures every project beneath it. Check the
terminal's current folder before running commands; `git rev-parse
--show-toplevel` shows which repo you're in. Fix: delete the stray `.git` (only
after confirming it has no commits) and `git init` in the project folder.
</details>

**Q: What is `JAVA_HOME`?**
<details><summary>Answer</summary>

An environment variable pointing at the JDK folder. Build tools like Maven read
it to find which Java to use, independent of PATH.
</details>
