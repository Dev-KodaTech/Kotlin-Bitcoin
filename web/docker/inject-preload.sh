#!/bin/sh
# Adds <link rel="preload" as="fetch" crossorigin> for every content-hashed .wasm of the distribution to index.html
# (research.md W-16). Their names only exist after the build, so index.html in the repository cannot list them.
set -eu

dist="${1:?usage: inject-preload.sh <distribution directory>}"
index="$dist/index.html"
links=""
for wasm in "$dist"/*.wasm; do
    [ -e "$wasm" ] || continue
    name=$(basename "$wasm")
    links="$links    <link rel=\"preload\" href=\"$name\" as=\"fetch\" crossorigin>\n"
done
[ -n "$links" ] || { echo "inject-preload.sh: no .wasm in $dist" >&2; exit 1; }

awk -v links="$links" '
    /<\/head>/ && !done { gsub(/\\n/, "\n", links); printf "%s", links; done = 1 }
    { print }
' "$index" > "$index.tmp"
mv "$index.tmp" "$index"
