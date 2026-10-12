#!/bin/bash
# Prepares everything the Android build needs that is not checked into git:
#   1. app/src/main/jniLibs/arm64-v8a/  Node.js + git (Termux builds), renamed to lib*.so and ELF-patched
#   2. app/src/main/assets/runtime/      CA bundle, openssl.cnf, Node preload shim
#   3. app/src/main/assets/st-bundle.zip SillyTavern source + production node_modules
#
# Inputs:
#   ST_SRC      SillyTavern git checkout to bundle (default ~/AI/SillyTavern, HEAD is exported)
#   vendor/debs Termux .deb packages (see tools/fetch_termux.sh)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ST_SRC="${ST_SRC:-$HOME/AI/SillyTavern}"
VENDOR="$ROOT/vendor"
STAGE="$ROOT/build-stage"
JNI="$ROOT/app/src/main/jniLibs/arm64-v8a"
ASSETS="$ROOT/app/src/main/assets"
PREFIX_REL="data/data/com.termux/files/usr"

echo "== Extracting Termux packages"
rm -rf "$VENDOR/root"; mkdir -p "$VENDOR/root"
for deb in "$VENDOR"/debs/*.deb; do
    tmp="$(mktemp -d)"
    tar -xf "$deb" -C "$tmp"
    tar -xf "$tmp"/data.tar.* -C "$VENDOR/root"
    rm -rf "$tmp"
done
U="$VENDOR/root/$PREFIX_REL"

echo "== Native libraries"
rm -rf "$JNI"; mkdir -p "$JNI"
# source path (relative to usr/) -> packaged name
cat > "$STAGE.libs.tsv" <<'EOF'
bin/node	libnode.so
bin/git	libgit.so
libexec/git-core/git-remote-http	libgitremotehttp.so
lib/libc++_shared.so	libc++_shared.so
lib/libz.so.1.3.2	libz1.so
lib/libcares.so	libcares.so
lib/libsqlite3.so	libsqlite3.so
lib/libcrypto.so.3	libcrypto3.so
lib/libssl.so.3	libssl3.so
lib/libicudata.so.78.3	libicudata78.so
lib/libicuuc.so.78.3	libicuuc78.so
lib/libicui18n.so.78.3	libicui18n78.so
lib/libpcre2-8.so	libpcre2-8.so
lib/libiconv.so	libiconv.so
lib/libcurl.so	libcurl.so
lib/libnghttp2.so	libnghttp2.so
lib/libnghttp3.so	libnghttp3.so
lib/libngtcp2.so	libngtcp2.so
lib/libngtcp2_crypto_ossl.so	libngtcp2_crypto_ossl.so
lib/libssh2.so	libssh2.so
EOF
RENAMES=(libz.so.1=libz1.so libcrypto.so.3=libcrypto3.so libssl.so.3=libssl3.so
         libicudata.so.78=libicudata78.so libicuuc.so.78=libicuuc78.so libicui18n.so.78=libicui18n78.so)
while IFS=$'\t' read -r src dst; do
    cp "$U/$src" "$JNI/$dst"
    chmod 755 "$JNI/$dst"
    python3 -I "$ROOT/tools/elfpatch.py" "$JNI/$dst" "${RENAMES[@]}"
done < "$STAGE.libs.tsv"
rm "$STAGE.libs.tsv"

echo "== Runtime assets"
rm -rf "$ASSETS/runtime"; mkdir -p "$ASSETS/runtime"
cp "$U/etc/tls/cert.pem" "$ASSETS/runtime/cert.pem"
cp "$U/etc/tls/openssl.cnf" "$ASSETS/runtime/openssl.cnf"
cp "$ROOT/runtime/android-preload.cjs" "$ASSETS/runtime/android-preload.cjs"

echo "== Tokenizers SillyTavern would otherwise download on first use"
# SillyTavern fetches these from GitHub the first time a matching model is used and caches them in
# <data>/_cache; shipping them makes token counting work offline and avoids a minutes-long first use.
mkdir -p "$VENDOR/tokenizers" "$ASSETS/seed-cache"
rm -f "$ASSETS/seed-cache/"*
for name in command-r command-a qwen2 nemo deepseek; do
    f="$VENDOR/tokenizers/$name.json.gz"
    [ -s "$f" ] || curl -sfL "https://github.com/SillyTavern/SillyTavern-Tokenizers/raw/main/$name.json.gz" -o "$f"
    gzip -t "$f"
    cp "$f" "$ASSETS/seed-cache/"
done

echo "== Third-party notices"
NOTICES="$ASSETS/THIRD_PARTY_NOTICES.txt"
cp "$VENDOR/debs/list.tsv" "$ROOT/runtime/termux-packages.tsv"
{
    echo "TavernDroid bundles the following third-party software."
    echo "Termux-built binaries come from https://packages-cf.termux.dev/apt/termux-main (build recipes:"
    echo "https://github.com/termux/termux-packages). Exact package versions and SHA-256 sums are listed in runtime/termux-packages.tsv"
    echo "of the TavernDroid source tree; their source code is available from the Termux and upstream projects."
    echo
    while IFS=$'\t' read -r name file _sha; do
        echo "================================================================================"
        echo "$name  ($(basename "$file"))"
        echo "================================================================================"
        for f in "$U/share/doc/$name"/{copyright,LICENSE,LICENCE.md,COPYING}; do
            [ -f "$f" ] && { cat "$f"; echo; break; }
        done
    done < "$VENDOR/debs/list.tsv"
    echo "================================================================================"
    echo "SillyTavern (AGPL-3.0) - https://github.com/SillyTavern/SillyTavern"
    echo "Its own dependencies' licenses are shipped inside st-bundle.zip (node_modules/*/LICENSE*)."
    echo "================================================================================"
} > "$NOTICES"

echo "== SillyTavern bundle from $ST_SRC"
rm -rf "$STAGE"; mkdir -p "$STAGE/st"
git -C "$ST_SRC" archive HEAD | tar -x -C "$STAGE/st"
ST_REV="$(git -C "$ST_SRC" rev-parse --short HEAD)"
# Bug fixes on top of the release (each written to be upstreamable); see patches/README.md.
PATCH_ID=""
if ls "$ROOT"/patches/*.patch >/dev/null 2>&1; then
    for patch in "$ROOT"/patches/*.patch; do
        echo "   applying $(basename "$patch")"
        (cd "$STAGE/st" && patch -p1 --quiet --forward < "$patch")
    done
    PATCH_ID="+p$(cat "$ROOT"/patches/*.patch | shasum | cut -c1-7)"
fi
ST_REV="$ST_REV$PATCH_ID"
ST_VER="$(python3 -I -c 'import json,sys;print(json.load(open(sys.argv[1]))["version"])' "$STAGE/st/package.json")"
# Production dependencies: reuse the checkout's node_modules, then prune dev-only packages offline.
rsync -a "$ST_SRC/node_modules" "$STAGE/st/"
(cd "$STAGE/st" && PATH="$HOME/.local/bin:$PATH" npm prune --omit=dev --no-audit --no-fund --offline >/dev/null)
# Drop files that are never loaded at runtime (source maps, type declarations, readmes, CLI shims).
find "$STAGE/st/node_modules" -type d -name .bin -prune -exec rm -rf {} +
find "$STAGE/st/node_modules" -type f \( -name '*.map' -o -name '*.d.ts' -o -name '*.d.mts' -o -name '*.d.cts' \
    -o -iname 'readme*' -o -iname 'changelog*' -o -iname 'history.md' \) -delete
rm -rf "$STAGE/st/tests" "$STAGE/st/colab" "$STAGE/st/docker" "$STAGE/st/.github"
printf '{"version":"%s","revision":"%s"}\n' "$ST_VER" "$ST_REV" > "$STAGE/st/.taverndroid-bundle.json"

mkdir -p "$ASSETS"
rm -f "$ASSETS/st-bundle.zip"
(cd "$STAGE/st" && zip -q -r -9 -y "$ASSETS/st-bundle.zip" .)
printf '%s-%s\n' "$ST_VER" "$ST_REV" > "$ASSETS/st-bundle.version"

du -sh "$JNI" "$ASSETS/st-bundle.zip"
echo "Bundled SillyTavern $ST_VER ($ST_REV)"
