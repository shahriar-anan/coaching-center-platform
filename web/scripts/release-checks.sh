# Checks a built website before it goes to the VPS. Sourced by release-build.sh
# (on the PC) and deploy-on-box.sh (on the box), so both run the same test.
#
#   check_release <release dir> <site url> <staff url>
#
# The browser bundle must call the API on its own host: NEXT_PUBLIC_API_BASE_URL
# baked in empty, with both host URLs baked in. An absolute API base makes the
# admin host's calls cross-site; the browser then drops the SameSite=Strict
# refresh cookie and every refresh logs the admin out (29 October 2026).
# Every check fails closed: a bundle it cannot read is refused.

check_release() {
  local dir=$1 site=$2 staff=$3
  local static="$dir/.next/static"
  local esc_site esc_staff found base

  if [ ! -f "$dir/server.js" ] || [ ! -d "$static" ]; then
    echo "REFUSED: $dir has no server.js or .next/static" >&2
    return 1
  fi

  # src/lib/config.ts builds the host config right after the API base, so the
  # baked {siteUrl,staffUrl} object anchors the API base literal before it.
  esc_site=$(printf '%s' "$site" | sed 's/[.]/\\./g')
  esc_staff=$(printf '%s' "$staff" | sed 's/[.]/\\./g')
  found=$(grep -rhoE "\(\"[^\"]*\"\),[^;]{0,60}\{siteUrl:\"$esc_site\",staffUrl:\"$esc_staff\"\}" "$static" | head -1 || true)
  if [ -z "$found" ]; then
    echo "REFUSED: the bundle does not carry siteUrl \"$site\" and staffUrl \"$staff\"." >&2
    echo "         Build with NEXT_PUBLIC_SITE_URL and NEXT_PUBLIC_STAFF_URL set (web/scripts/release-build.sh)." >&2
    return 1
  fi
  base=$(printf '%s' "$found" | sed -E 's/^\("([^"]*)"\).*/\1/')
  if [ -n "$base" ]; then
    echo "REFUSED: the bundle calls the API at \"$base\"; it must be empty so each host calls its own /api/." >&2
    return 1
  fi

  if grep -rqE 'localhost:[0-9]{2,5}' "$static"; then
    echo "REFUSED: a localhost URL is baked into the browser bundle." >&2
    return 1
  fi

  echo "release check passed: API base empty, siteUrl $site, staffUrl $staff"
}
