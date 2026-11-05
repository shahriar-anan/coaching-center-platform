#!/usr/bin/env bash
# Read-only checks before an SSLCommerz payment test on the exam server. Changes nothing, prints no secret value.
# Guide: docs/exam-site/plans/live-domain-and-sslcommerz.md.
#
# Run from PowerShell on the PC (Git Bash ssh is refused by the box), as root:
#
#   Get-Content -Raw docs/exam-site/deploy/live-payment-preflight.sh |
#     ssh exam-kvm "tr -d '\r' | sed '1s/^\xEF\xBB\xBF//' | MODE=live bash -s"
#
# MODE    sandbox (staging profile, sandbox store) or live (prod profile, live store). Default sandbox.
# SITE    student host. Default symamlaw.com.
# STAFF   admin host. Default admin.symamlaw.com.
# BOX_IP  the address both hosts must resolve to. Default 187.52.115.111.
set -u

MODE=${MODE:-sandbox}
SITE=${SITE:-symamlaw.com}
STAFF=${STAFF:-admin.symamlaw.com}
BOX_IP=${BOX_IP:-187.52.115.111}
ENV_FILE=/etc/coaching-api.env

fails=0
pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1"; fails=$((fails + 1)); }
warn() { echo "WARN  $1"; }

# The value of one env line, for comparing only. Never echo a secret with this.
val() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- | sed -E 's/^"(.*)"$/\1/'; }
has() { [ -n "$(val "$1")" ]; }
host_of() { printf '%s' "$1" | sed -E 's#^[a-z]+://##; s#[/:].*$##'; }

case "$MODE" in
  sandbox) want_profile=staging; want_gateway=sandbox.sslcommerz.com ;;
  live) want_profile=prod; want_gateway=securepay.sslcommerz.com ;;
  *) echo "MODE must be sandbox or live" >&2; exit 2 ;;
esac
echo "== exam server preflight: MODE=$MODE SITE=$SITE STAFF=$STAFF"

echo "-- API environment ($ENV_FILE)"
[ -r "$ENV_FILE" ] || { fail "cannot read $ENV_FILE (run as root)"; exit 1; }
profile=$(val SPRING_PROFILES_ACTIVE)
[ "$profile" = "$want_profile" ] && pass "SPRING_PROFILES_ACTIVE=$profile" || fail "SPRING_PROFILES_ACTIVE is '$profile', want $want_profile"
for name in SSLCZ_STORE_ID SSLCZ_STORE_PASSWORD SSLCZ_BASE_URL SSLCZ_CALLBACK_BASE_URL; do
  has "$name" && pass "$name is set" || fail "$name is empty or missing"
done
gateway=$(host_of "$(val SSLCZ_BASE_URL)")
[ "$gateway" = "$want_gateway" ] && pass "SSLCZ_BASE_URL host is $gateway" || fail "SSLCZ_BASE_URL host is '$gateway', want $want_gateway"
callback=$(val SSLCZ_CALLBACK_BASE_URL)
[ "$callback" = "https://$SITE" ] && pass "SSLCZ_CALLBACK_BASE_URL=https://$SITE" || fail "SSLCZ_CALLBACK_BASE_URL is '$callback', want https://$SITE (no path, no trailing slash)"
origin1=$(val WEB_ORIGIN | cut -d, -f1 | tr -d ' ')
origin2=$(val WEB_ORIGIN | cut -d, -f2 | tr -d ' ')
[ "$origin1" = "https://$SITE" ] && pass "WEB_ORIGIN first is the student host" || fail "WEB_ORIGIN first is '$origin1', want https://$SITE (payment returns and mail links use it)"
[ "$origin2" = "https://$STAFF" ] && pass "WEB_ORIGIN second is the admin host" || fail "WEB_ORIGIN second is '$origin2', want https://$STAFF"
for name in SEED_ENABLED SEED_DEMO SEED_SAMPLE SEED_DEV; do
  [ "$(val "$name" | tr 'A-Z' 'a-z')" = "true" ] && fail "$name=true (the prod profile refuses to start)" || true
done
grep -qE '^MASTER_ADMIN_PASSWORD=' "$ENV_FILE" && warn "MASTER_ADMIN_PASSWORD is still in the env file; remove it (decisions.md, Master Admin at delivery)"
grep -qE '^MAIL_TEST_ENABLED=' "$ENV_FILE" && warn "MAIL_TEST_ENABLED is unused since 25 October; delete the line"

echo "-- website service"
unit=$(systemctl show coaching-web -p Environment --value | tr ' ' '\n')
[ "$(printf '%s\n' "$unit" | sed -n 's/^SITE_URL=//p')" = "https://$SITE" ] && pass "coaching-web SITE_URL=https://$SITE" || fail "coaching-web SITE_URL is not https://$SITE"
[ "$(printf '%s\n' "$unit" | sed -n 's/^STAFF_URL=//p')" = "https://$STAFF" ] && pass "coaching-web STAFF_URL=https://$STAFF" || fail "coaching-web STAFF_URL is not https://$STAFF"
for svc in coaching-api coaching-web nginx; do
  systemctl is-active --quiet "$svc" && pass "$svc is running" || fail "$svc is not running"
done

echo "-- DNS and certificates"
for host in "$SITE" "$STAFF"; do
  # Public DNS, as SSLCommerz and Let's Encrypt see it; /etc/hosts maps the box's own name to 127.0.1.1.
  if command -v dig >/dev/null 2>&1; then
    ip=$(dig +short A "$host" @1.1.1.1 | grep -E '^[0-9.]+$' | head -1)
  else
    ip=$(getent ahostsv4 "$host" | awk 'NR==1{print $1}')
  fi
  [ "$ip" = "$BOX_IP" ] && pass "$host resolves to $BOX_IP" || fail "$host resolves to '${ip:-nothing}', want $BOX_IP (Namecheap A record)"
  end=$(echo | openssl s_client -connect "$host:443" -servername "$host" 2>/dev/null | openssl x509 -noout -enddate -checkhost "$host" 2>/dev/null)
  if printf '%s' "$end" | grep -q "does match"; then
    pass "$host certificate matches, $(printf '%s' "$end" | sed -n 's/^notAfter=/valid to /p')"
  else
    fail "$host has no matching certificate (Certbot, phase 2)"
  fi
done

echo "-- API over the public address"
status=$(curl -s --max-time 15 "https://$SITE/api/v1/public/exam-site/status")
printf '%s' "$status" | grep -q '"checkoutEnabled":true' && pass "checkout is enabled" || fail "checkout is not enabled (the four SSLCZ_* values, then restart the API)"
# An IPN for a transaction that does not exist reaches the API and gets its 404. It writes nothing.
ipn=$(curl -s --max-time 15 -o /tmp/preflight-ipn.$$ -w '%{http_code}' -X POST \
  -H 'Content-Type: application/x-www-form-urlencoded' --data 'tran_id=preflight0check' \
  "https://$SITE/api/v1/live-exam-orders/preflight0check/ipn")
if [ "$ipn" = "404" ] && grep -q 'RESOURCE_NOT_FOUND' /tmp/preflight-ipn.$$; then
  pass "IPN path reaches the API without a login"
else
  fail "IPN path answered $ipn without the API's RESOURCE_NOT_FOUND (Nginx, basic auth or the API)"
fi
rm -f /tmp/preflight-ipn.$$

echo "-- last API start"
since=$(systemctl show coaching-api -p ActiveEnterTimestamp --value)
if journalctl -u coaching-api --since "$since" --no-pager 2>/dev/null | grep -q 'IllegalStateException'; then
  fail "the API logged an IllegalStateException since its last start (profile guard or missing env)"
else
  pass "no startup refusal since $since"
fi

echo "== $fails failed"
[ "$fails" -eq 0 ]
