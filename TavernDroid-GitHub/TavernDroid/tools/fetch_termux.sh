#!/bin/bash
# Downloads the Termux aarch64 packages (Node.js LTS + git and their dependencies) into vendor/debs,
# verifying every file against the SHA256 in the repository index.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="${TERMUX_REPO:-https://packages-cf.termux.dev/apt/termux-main}"
DEST="$ROOT/vendor/debs"
mkdir -p "$DEST"
INDEX="$(mktemp)"
curl -sfL "$REPO/dists/stable/main/binary-aarch64/Packages" -o "$INDEX"
python3 -I - "$INDEX" nodejs-lts git <<'PY' > "$DEST/list.tsv"
import re, sys
pk = {}
for blk in open(sys.argv[1]).read().split('\n\n'):
    d = dict(l.split(': ', 1) for l in blk.split('\n') if ': ' in l and not l.startswith(' '))
    if 'Package' in d:
        pk[d['Package']] = d
seen = set()
def walk(n):
    if n in seen:
        return
    seen.add(n)
    for dep in re.split(r',\s*', pk[n].get('Depends', '')):
        dep = dep.split(' ')[0].split('|')[0].strip()
        if dep:
            walk(dep)
for root in sys.argv[2:]:
    walk(root)
for n in sorted(seen):
    print(n, pk[n]['Filename'], pk[n]['SHA256'], sep='\t')
PY
rm "$INDEX"
while IFS=$'\t' read -r name file sha; do
    out="$DEST/$(basename "$file")"
    [ -f "$out" ] || curl -sfL "$REPO/$file" -o "$out"
    echo "$sha  $out" | shasum -a 256 -c - >/dev/null || { echo "checksum mismatch: $out"; exit 1; }
    echo "ok $name"
done < "$DEST/list.tsv"
