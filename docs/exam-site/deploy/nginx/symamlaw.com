# Student site on the client's main domain. Copy of sites-available/exam.symamlaw.com with only server_name changed.
# Install as /etc/nginx/sites-available/symamlaw.com, link it into sites-enabled, then run
#   certbot --nginx -d symamlaw.com -d www.symamlaw.com --redirect
# which adds the 443 listener, the certificate and the port 80 redirect to both blocks.
# www only redirects, so sign-in cookies and the Google callback stay on one host.
server {
    server_name www.symamlaw.com;
    return 308 https://symamlaw.com$request_uri;
}

server {
    server_name symamlaw.com;

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
