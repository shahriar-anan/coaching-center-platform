# nginx access log with timings (exam-kvm)

Applied 22 Nov 2026, 01:01 Dhaka (PDF viewer plan, Stage 0). In `/etc/nginx/nginx.conf`, the `http` block's
`access_log /var/log/nginx/access.log;` became:

```nginx
	# Combined plus timings (22 Nov 2026, PDF viewer plan Stage 0). New fields go last, so field positions stay.
	log_format timed '$remote_addr - $remote_user [$time_local] "$request" $status $body_bytes_sent "$http_referer" "$http_user_agent" rt=$request_time urt=$upstream_response_time';
	access_log /var/log/nginx/access.log timed;
```

- `rt` is the whole request as nginx saw it, including sending the body to the client: for a PDF, roughly the download
  time. `urt` is the time the API or website took to answer.
- The line is "combined" with two fields appended, so scripts that read fields by position still work. The only
  fail2ban jail is `sshd`, so nothing parses this log for bans.
- Backup: `/etc/nginx/nginx.conf.before-timed-20261121-190135`. An identical earlier backup
  (`...-190120`, from a first attempt that stopped before editing) can be deleted.
- Rollback: copy the backup back, `nginx -t`, `systemctl reload nginx`.

Analysis script: [../../reports/pdf-log-probe.sh](../../reports/pdf-log-probe.sh).
