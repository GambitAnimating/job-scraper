# AI log

Prompts given to Claude Code (Opus 5.5) on this project, verbatim, with a
one-line summary of what it produced.

## 2026-11-19

- **Prompt:** "Before doing this we gotta free some space. Where can we do that?" then "Do it"
  **Result:** Measured disk usage and cleared the user Temp folder (30.6 GB freed).
- **Prompt:** "for maven No package found matching input criteria."
  **Result:** Maven isn't on winget; downloaded Maven 3.9.16 from Apache, verified its SHA-512 checksum, unpacked it to `C:\Users\lumar\tools`, added it to PATH, and confirmed it runs on JDK 21.
- **Prompt:** "Dont just tell me to remember it, write it down somewhere for studying or something."
  **Result:** Created `study-guide.md` (interview questions with collapsed answers) and added a rule to `CLAUDE.md` that Claude keeps it updated.
- **Prompt:** "All done" (after creating the database login and user)
  **Result:** Verified `scraper_app` user and roles in SQL Server, gave the `setx` commands for `DB_URL`/`DB_USER`/`DB_PASSWORD`, and drafted `ConnectionCheck.java` (JDBC `SELECT 1` with try-with-resources) with a line-by-line explanation.
- **Prompt:** chose "Split it" for how CLAUDE.md goes on GitHub
  **Result:** Moved the resume, email and interview-prep section into a private, git-ignored `CLAUDE.local.md`; the public `CLAUDE.md` keeps the working rules and checklist.
