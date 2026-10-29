# Swaps a packed website release into /opt/coaching-web on the VPS. Not run
# directly: web/scripts/deploy-web.ps1 sends release-checks.sh, then the
# variables PKG, EXPECTED_SHA and DRY_RUN, then this file, to `bash -s` as root.
#
# The staged release must pass check_release against the host URLs the running
# service uses; otherwise nothing is swapped. If the new release is not healthy
# within 30 s, the previous one is put back.
set -euo pipefail

LIVE=/opt/coaching-web
TS=$(date -u +%Y%m%d-%H%M%S)
NEW=$LIVE.new-$TS
OLD=$LIVE.before-$TS

unit_env() {
  systemctl show coaching-web -p Environment --value | tr ' ' '\n' | sed -n "s/^$1=//p"
}
site=$(unit_env SITE_URL)
staff=$(unit_env STAFF_URL)
if [ -z "$site" ] || [ -z "$staff" ]; then
  echo "REFUSED: coaching-web.service has no SITE_URL or STAFF_URL." >&2
  exit 1
fi

if [ "$(sha256sum "$PKG" | cut -c1-64)" != "$EXPECTED_SHA" ]; then
  echo "REFUSED: $PKG does not match the uploaded package's SHA-256." >&2
  exit 1
fi

echo "== stage $NEW"
mkdir "$NEW"
trap 'rm -rf "$NEW"' EXIT
tar -xzf "$PKG" -C "$NEW"

# The PC build carries sharp's Windows binary; the running release has the Linux ones.
want=$(node -p "require('$NEW/node_modules/sharp/package.json').version")
have=$(node -p "require('$LIVE/node_modules/sharp/package.json').version")
if [ "$want" != "$have" ]; then
  echo "REFUSED: sharp $want in the release, $have on the box. Swap the Linux binaries by hand (VPS plan, slice 0 step 5)." >&2
  exit 1
fi
rm -rf "$NEW/node_modules/@img/sharp-win32-x64"
cp -a "$LIVE/node_modules/@img/sharp-linux-x64" "$LIVE/node_modules/@img/sharp-libvips-linux-x64" "$NEW/node_modules/@img/"
chown -R deploy:deploy "$NEW"

echo "== check"
check_release "$NEW" "$site" "$staff"
nonlinux=$(find "$NEW" -name '*.node' | grep -v linux || true)
if [ -n "$nonlinux" ]; then
  echo "REFUSED: non-Linux native modules: $nonlinux" >&2
  exit 1
fi
test -f "$NEW/public/pdf.worker.min.mjs" || { echo "REFUSED: public/ is missing" >&2; exit 1; }
echo "build $(cat "$NEW/.next/BUILD_ID")"

if [ "${DRY_RUN:-0}" = "1" ]; then
  echo "dry run: release checked, nothing swapped"
  exit 0
fi

echo "== swap"
trap - EXIT
mv "$LIVE" "$OLD"
mv "$NEW" "$LIVE"
systemctl restart coaching-web

site_host=${site#https://}
staff_host=${staff#https://}
for i in $(seq 1 30); do
  sleep 1
  a=$(curl -s -o /dev/null -w '%{http_code}' -H "Host: $staff_host" http://127.0.0.1:3000/login || true)
  s=$(curl -s -o /dev/null -w '%{http_code}' -H "Host: $site_host" http://127.0.0.1:3000/ || true)
  if [ "$a" = "200" ] && [ "$s" = "200" ]; then
    echo "up after ${i}s (admin /login $a, student / $s)"
    echo "previous release kept at $OLD"
    rm -f "$PKG"
    exit 0
  fi
done
echo "not healthy (admin $a, student $s), rolling back" >&2
mv "$LIVE" "$NEW.failed"
mv "$OLD" "$LIVE"
systemctl restart coaching-web
exit 1
