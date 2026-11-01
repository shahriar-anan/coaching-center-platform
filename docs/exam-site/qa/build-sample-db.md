# Build the sample database

This builds a new, empty PostgreSQL database and fills it with the sample seed: eight categories, three packages, twenty exams, six students, two staff, two teachers, 22 sittings, eight orders, seven grants, seven reviews, six announcements and their notices. The data is fixed, and the dates move with the day you run it. The seed is described in `seed-map.md`, and the test checklist uses it (`QA-Checklist.md`, section 0b).

The seed inserts once. It never changes a database that already holds this data, and it refuses a database that holds rows of another seed. So to rebuild, use a **new** database name (or drop the old one and create it again). Do not run it on `coaching_center_dev` or on the staging database.

You need: PostgreSQL 17 running on `localhost:5432`, and the API's `api/.env` already set up (the database user and password in `DB_USERNAME` and `DB_PASSWORD`, and the Master Admin in `MASTER_ADMIN_*`). The API reads `api/.env` in the dev profile.

Keep the secrets in the PowerShell window only. Do not save them in a file.

## 1. Create an empty database

From the repository root, in PowerShell (replace `postgres` with the user in `api/.env` if different):

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -h localhost -U postgres -d postgres -c "CREATE DATABASE coaching_center_sample"
```

It asks for the password of that user.

## 2. Set the variables for the run (same window as step 3)

```powershell
cd F:\coaching-center-platform\api
$env:DB_URL = "jdbc:postgresql://localhost:5432/coaching_center_sample"
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SEED_SAMPLE = "true"
$env:SEED_ENABLED = "false"
$env:SEED_DEV = "false"
$env:SEED_SAMPLE_PASSWORD = Read-Host "Sample password (8 to 72 characters)"
```

`Read-Host` shows what you type, so choose the password before you start and type it carefully. It is the password you will use to sign in as every sample account except the Master Admin. Write it down somewhere safe, outside the repository.

Notes:

- `SEED_SAMPLE=true` turns the seed on. The seed refuses the `prod` profile, so `SPRING_PROFILES_ACTIVE` must stay `dev` (or `staging`).
- `SEED_ENABLED`, `SEED_DEV` and `SEED_DEMO` are set off, so no other seed runs in the same start.
- `DB_URL` points the API at the new database. The user and password come from `api/.env`.

## 3. Start the API once, so the seed runs

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8092"
```

If you normally start the API another way, do that instead, in the same window. The port 8092 is the one the QA checklist uses.

Watch the console. Flyway runs the migrations, then the seed runs once at startup (the `SampleSeedRunner`). A clean start with no error and the normal "started" line means the data is in. The seed logs nothing on success.

If the start fails with a message from the seed, read it. The common ones:

- `SEED_SAMPLE_PASSWORD is required.` or `must be 8 to 72 characters.`: set the variable and start again.
- `Rows of another seed are in the database`: the database is not new. Use a new database name (step 1).
- `The Master Admin must exist`: the Master Admin is created at startup from `MASTER_ADMIN_*`. Check `api/.env`.

When the start is clean, stop the API with Ctrl+C. You can then start it normally against the same database, and the seed does nothing on a restart.

## 4. Check the key endpoints

Still in the same window, set the two secrets from the environment (they are read, never printed):

```powershell
$env:MASTER_ADMIN_PASSWORD = Read-Host "Master Admin password (from api/.env, MASTER_ADMIN_PASSWORD)"
```

Start the API again (step 3 command, without the seed variables is fine, but the same settings also work) and in a second PowerShell window, or after the start is up, run:

```powershell
.\docs\exam-site\qa\scripts\check-sample-db.ps1 -BaseUrl http://localhost:8092
```

The script signs in as Student 1, Student 2, Student 6, Teacher 1, Teacher 2 and the Master Admin, then calls the key endpoints. Each check prints `PASS` or `FAIL` with the expected count. A clean run ends with `All checks passed.`. The exit code is the number of failed checks.

What it checks, with the expected value:

| Check | Expected |
|---|---|
| Sign-in of six accounts | a token each |
| Public category tree has two roots; Short Courses is empty | present and empty |
| Published packages | 3 |
| Popular filter shows package 2 | found |
| Student 1 results list | 5 |
| Student 6 results list is empty | 0 |
| Student 6 inbox, announcements | 1 |
| Package 2 announcement: holder sees it, non-holder does not | 1 and 0 |
| Leaderboard of exam 1 (passers listed) | 3 rows; 2 below the pass mark (staff count) |
| Announcements sent (master) | 6 |
| Review average, exam 1 and package 2 | 4.7 from three ratings |
| Pending orders queue | 1 |
| Marking queue, Teacher 1 | 1 |
| Marking queue, Teacher 2 | 2 |

## 5. Sign in and look

Open the site (`http://localhost:3000` with the web app running) and sign in as one of the sample accounts in `QA-Checklist.md`, section 0b, with the sample password. Then follow `seed-map.md` for each feature.

## If a check fails

- A **sign-in** fails: the sample password in the window is not the one you used in step 2, or the API did not start on this database. Check the console for the seed message, and confirm `DB_URL` in the window points to `coaching_center_sample`.
- A **count** is off by a few: the database may have been built before a change to the seed. Rebuild in a new database name.
- A **review average** is not 4.7: check that the reviews were not edited in the database after the build.
- Everything fails with a connection error: the API is not running on port 8092. Start it (step 3), or pass the right `-BaseUrl`.

## Rebuild or time-shift

The sample is built for the day you run it. After about 60 days the "upcoming" exams are in the past, so build a fresh database when you need a new run. The cleanup of old sample databases is a separate step, not part of this guide.
