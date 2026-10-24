# symamlaw production backup

Taken on 23 October 2026 from the current Hostinger VPS (`client-vps`, `srv633348`, `88.222.215.187`). This is a copy only. The live app was not edited. No service was restarted. DNS, Nginx, and certificates were not changed. The dump was not imported. The other sites on this VPS were not copied.

The server copy lived in `/root/symamlaw-backup-20261023` (root, mode `700`) while it was being made. After the workstation copy matched, that directory was deleted. Nothing else on the server was removed.

## Workstation copy

`F:\symamlaw-backup-20261023`

That folder is outside this Git repository. It holds the database dump, the upload archive, and the env files. Do not commit it.

| File | Bytes | Source |
|---|---:|---|
| `symamlaw_db.sql.gz` | 2,756,761 | MariaDB `symamlaw_db` |
| `symamlaw-uploads.tar` | 67,635,200 | Laravel upload trees below |
| `symamlaw-backend.env` | 1,748 | `/var/www/html/symamlaw-backend/.env` |
| `symamlaw-frontend.env` | 350 | `/root/symamlaw/symamlaw-frontend/.env` |
| `symamlaw-frontend.env.save` | 351 | `/root/symamlaw/symamlaw-frontend/.env.save` |
| `symamlaw-admin.env` | 188 | `/root/symamlaw/symamlaw-admin/.env` |

The env files contain live secrets. Their values are not in this report.

## Database

`symamlaw_db` at measurement time: 48 tables, all InnoDB, 28,278,784 bytes in `information_schema` (`data_length + index_length`).

Dump command: `mysqldump --single-transaction --routines --triggers --events --default-character-set=utf8mb4 --databases symamlaw_db`, then gzip. No password was passed on the command line. `mysqldump` wrote no warnings. The gzip member is a MariaDB 10.11.14 dump of `symamlaw_db`, completed on the server at 2026-10-23 11:10:08. It contains 48 `CREATE TABLE` statements and a routines section.

Dump checksum (SHA-256):

```
68a7c51a41b2595786e4fab06e4950573eb90d4b9400bab805e592aec4511b64
```

`gzip -t` succeeded on the workstation copy. The workstation SHA-256 and byte size match the server file.

## Uploaded files

Measured with `du -sb` before the archive was made:

| Path | Bytes | `du -sh` |
|---|---:|---|
| `/var/www/html/symamlaw-backend/storage/app` | 11,876,399 | 12M |
| `/var/www/html/symamlaw-backend/public/documents` | 2,138,629 | 2.1M |
| `/var/www/html/symamlaw-backend/public/images` | 53,355,798 | 52M |

`symamlaw-uploads.tar` was created from the Laravel root and contains only those three trees. It has 333 entries, the same count as `find` on the three trees. `storage/logs`, `storage/framework`, and `vendor` are not in the archive.

Archive checksum (SHA-256), matching on the server and the workstation:

```
181c84b83739b424abcfe7e9261df8ec27bf81fd4358739996852dd30dbf7dee
```

## Left on the server

The live Laravel app, both Next.js apps, MariaDB, Nginx, and the uploaded files are still in place. This backup did not change them. `/root/symamlaw-backup-20261023` is gone.

## Other databases

These application databases are on the same MariaDB instance. They were measured and not dumped. Their files and env files were not copied.

| Database | Tables | Bytes |
|---|---:|---:|
| `hatipara_ss_hospital_db` | 29 | 1,523,712 |
| `old_otonest_db` | 85 | 2,260,992 |
| `otonest_db_new` | 113 | 380,108,800 |

`mysql`, `information_schema`, `performance_schema`, and `sys` are server catalogs. They were not dumped.

## Unreadable paths

None. The three upload trees, the four env files, and `symamlaw_db` were all readable.
