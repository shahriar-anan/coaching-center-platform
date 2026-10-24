# Prompt: Back up symamlaw production data

You are taking over a backup of the live symamlaw site on the current Hostinger VPS. Shahriar will buy a new KVM 2 later. This task is the backup only. Do not migrate, import, or change DNS.

Read `docs/exam-site/domain-ssl-migration-audit.md` and `docs/exam-site/decisions.md` first. The audit is the server record. Decisions override older deployment notes.

## Outcome

A restorable copy of the symamlaw production data, stored off the server and outside Git, plus a short report at `docs/exam-site/production-backup.md`.

Production data means:

- The MariaDB database `symamlaw_db` (schema and rows).
- Uploaded files under the Laravel app.
- The env files required to read that app, copied only into the off-Git backup folder.

It does not mean the Next.js `node_modules` trees, Laravel caches, sessions, compiled views, or log files.

## Server

SSH alias: `client-vps`. Public IPv4: `88.222.215.187`. Hostname: `srv633348`. Ubuntu 24.04. MariaDB 10.11 listens on `127.0.0.1:3306` only. Root can use the local MariaDB socket. Do not put the database password on a command line, in the report, or in Git.

Paths:

| What | Path |
|---|---|
| Database | `symamlaw_db` |
| Laravel app | `/var/www/html/symamlaw-backend` |
| Uploaded app files | `/var/www/html/symamlaw-backend/storage/app` (about 12 MB on 23 Oct 2026, includes `public/photos`) |
| Documents | `/var/www/html/symamlaw-backend/public/documents` (about 2 MB, includes `routine`) |
| Images | `/var/www/html/symamlaw-backend/public/images` (about 52 MB: `student`, `books`, `blogs`, `admin`, `cms`, `package`) |
| Backend env | `/var/www/html/symamlaw-backend/.env` |
| Frontend env | `/root/symamlaw/symamlaw-frontend/.env` and `.env.save` |
| Admin env | `/root/symamlaw/symamlaw-admin/.env` |

Other databases exist on this same server: `hatipara_ss_hospital_db`, `old_otonest_db`, `otonest_db_new`. They belong to other sites. Do not dump them, tar them, or copy their env files. Record their names in the report and stop there. `mysql`, `information_schema`, `performance_schema`, and `sys` are server catalogs, not application data.

## Hard stops

Do not edit, create, move, or delete application files. Do not install packages. Do not stop, restart, reload, or disable services. Do not change Nginx, DNS, certificates, firewall rules, or application env files. Do not import the dump into MariaDB. Do not run `migrate`, `db:seed`, `queue:restart`, or `php artisan` commands that write. Do not print private keys, passwords, or tokens in the report or in chat.

You may create one new backup directory on the server, write the dump and archives there, copy them to Shahriar's machine, then delete only that backup directory after the copy is verified. Create nothing under the Laravel `public` tree or inside the Git repo.

The local copy must sit outside the repository. `docs/exam-site/config-files/` is gitignored and already holds payment notes. Do not add SQL dumps or env files there. Do not `git add` the backup.

## Steps

1. Confirm `ssh -o BatchMode=yes client-vps` works. If it does not, stop and say what access is missing.
2. Re-measure. Run `du -sh` on the three upload paths. Query `information_schema.tables` for `symamlaw_db` size and table count. Query the other database names and sizes only. Do not dump them.
3. On the server, create `/root/symamlaw-backup-YYYYMMDD` owned by root, mode `700`.
4. Dump `symamlaw_db` with `mysqldump --single-transaction --routines --triggers --events --default-character-set=utf8mb4`, compressed with gzip, into that directory. `--single-transaction` is the InnoDB-consistent read. Do not pass `--lock-all-tables`. Do not add the password to the process list.
5. Tar, from the Laravel root, only these trees: `storage/app`, `public/documents`, `public/images`. Do not include `storage/logs`, `storage/framework`, or `vendor`.
6. Copy the three env files listed above into that same directory. They contain live secrets. They travel only to the off-Git folder.
7. From the workstation, `scp -r` the backup directory to a folder outside `f:\coaching-center-platform`. Suggested path: `F:\symamlaw-backup-YYYYMMDD`.
8. Verify on the workstation: `gzip -t` on the dump, file counts in the tar, and byte sizes matching the server. Then delete `/root/symamlaw-backup-YYYYMMDD` and nothing else.
9. Write `docs/exam-site/production-backup.md` with the date, dump byte size, table count, upload byte sizes, the workstation path, and the checksum of the dump (`sha256sum`). State that the other databases were not dumped. Do not include secret values.

## Report shape

- What was backed up, with sizes.
- Where the workstation copy is.
- Dump checksum.
- What was left on the server (the live app, untouched).
- The other database names and sizes, and that they were not copied.
- Anything that could not be read.

## Stop

Stop after the report. Do not restore the dump, change DNS, buy or configure the KVM 2, or write application code. Wait for Shahriar.
