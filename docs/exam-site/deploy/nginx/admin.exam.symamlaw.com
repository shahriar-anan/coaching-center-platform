# Admin site on the client's domain. Copy of sites-available/coaching-admin with only server_name changed.
# Same API and website as the student host; the website decides what each host shows.
# Install as /etc/nginx/sites-available/admin.exam.symamlaw.com, link it into sites-enabled, then run Certbot.
# Guide: docs/exam-site/plans/live-domain-and-sslcommerz.md, phase 2.
server {
    listen 80;
    listen [::]:80;
    server_name admin.exam.symamlaw.com;

    client_max_body_size 105m;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_send_timeout 120s;
    }

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_read_timeout 60s;
    }
}
