#!/usr/bin/env bash
# Builds the website for the VPS and packs it. Run from Git Bash on the PC:
#
#   bash web/scripts/release-build.sh <out.tgz>
#
# Then deploy it with web/scripts/deploy-web.ps1. The build env is fixed here,
# not typed by hand: the API base is empty (each host calls its own /api/) and
# both host URLs are baked in. The hosts are required, so a release always names
# the hosts it was built for; nothing falls back to an old address.
#
#   SITE_URL=https://exam.symamlaw.com STAFF_URL=https://admin.symamlaw.com \
#     bash web/scripts/release-build.sh <out.tgz>
#
# Under Git Bash an empty variable reaches Next; in PowerShell `$env:X=''`
# deletes it and web/.env.local's localhost URL would win.
set -euo pipefail

out=${1:?usage: release-build.sh <out.tgz>}
site=${SITE_URL:?set SITE_URL to the student host, e.g. https://exam.symamlaw.com}
staff=${STAFF_URL:?set STAFF_URL to the admin host, e.g. https://admin.symamlaw.com}

root=$(git rev-parse --show-toplevel)
web="$root/web"
. "$web/scripts/release-checks.sh"

if [ -n "$(git -C "$root" status --porcelain)" ]; then
  echo "Commit or stash first: a release is built from a clean commit." >&2
  exit 1
fi
lock="$root/.heavy.lock"
if [ -e "$lock" ]; then
  echo "Heavy lock held by: $(cat "$lock")" >&2
  exit 1
fi
echo "release-build $(date -Iseconds)" > "$lock"
trap 'rm -f "$lock"' EXIT

cd "$web"
rm -rf .next
NEXT_PUBLIC_API_BASE_URL= \
NEXT_PUBLIC_SITE_URL="$site" \
NEXT_PUBLIC_STAFF_URL="$staff" \
NEXT_PUBLIC_GOOGLE_ENABLED=true \
  npm run build

# Lay the release out as it runs on the box, then check that layout.
mkdir -p .next/standalone/.next
rm -rf .next/standalone/.next/static
cp -r .next/static .next/standalone/.next/static
check_release "$web/.next/standalone" "$site" "$staff"

native=$(find .next/standalone -name '*.node' -not -path '*/sharp-win32-x64/*')
if [ -n "$native" ]; then
  echo "Native modules besides sharp-win32-x64 (need a Linux build):" >&2
  echo "$native" >&2
  exit 1
fi

tar --force-local -czf "$out" --exclude='./node_modules/@img/sharp-win32-x64' -C .next/standalone . -C "$web" public
echo "commit $(git -C "$root" rev-parse --short HEAD), build $(cat .next/BUILD_ID)"
echo "package $out"
echo "sha256 $(sha256sum "$out" | cut -c1-64)"
