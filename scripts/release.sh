#!/bin/bash
# Publish a release: ./scripts/release.sh 0.3.0
#
# Checks that the working tree is clean, on main, in sync with GitHub, and that
# CHANGELOG.md has a "## [<version>]" section; then creates and pushes the tag v<version>.
# The "Build" workflow then builds, signs and publishes the GitHub Release with the APK.
set -euo pipefail

V="${1:-}"
[[ "$V" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Usage: $0 <major.minor.patch>  e.g. $0 0.3.0" >&2; exit 1; }
cd "$(dirname "$0")/.."

[ "$(git rev-parse --abbrev-ref HEAD)" = "main" ] || { echo "Not on main" >&2; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "Working tree not clean" >&2; exit 1; }
git fetch -q origin main --tags
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || { echo "main is not in sync with origin/main" >&2; exit 1; }
git rev-parse -q --verify "refs/tags/v$V" >/dev/null && { echo "Tag v$V already exists" >&2; exit 1; }
grep -q "^## \[$V\]" CHANGELOG.md || { echo "CHANGELOG.md has no '## [$V]' section" >&2; exit 1; }

git tag -a "v$V" -m "Limits for Claude $V"
git push -q origin "v$V"
echo "Pushed tag v$V. The release will appear in ~3 minutes: https://github.com/Tattanu/limits-for-claude/releases/tag/v$V"
