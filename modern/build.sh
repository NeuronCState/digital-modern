#!/usr/bin/env bash
# Digital Modern build script
# ----------------------------------------------------------------------------
#  A self-contained build driver for the modern/ overlay. Targets Java 21,
#  matches the rest of the upgrade stack (XStream 1.4.21, SLF4J 2.0.16,
#  FlatLaf 3.7, modern Maven plugin stack), and optionally produces a
#  standalone GraalVM native image (~54 MB, no JVM needed at runtime).
#
#  Targets:
#    ./build.sh info        show detected toolchain
#    ./build.sh deps        download missing JDK / GraalVM to .runtime/
#    ./build.sh clean       remove .build, modern/dist, target/
#    ./build.sh jar         produce modern/dist/Digital.jar
#    ./build.sh vibrancy    build libDigitalVibrancy.dylib (macOS sidebar effect)
#    ./build.sh iconset     rebuild macOS Digital.iconset from icon.svg
#    ./build.sh jlink       produce modern/dist/Digital (custom runtime image)
#    ./build.sh app         produce modern/dist/Digital.app (macOS app bundle)
#    ./build.sh dmg         produce modern/dist/Digital.dmg (macOS installer)
#    ./build.sh native      produce modern/dist/digital (GraalVM native image)
#    ./build.sh all         jar + vibrancy + app + native
#
#  Environment overrides:
#    JDK21    - path to a JDK 21 installation
#    GRAALVM  - path to a GraalVM 21 installation
#    MAVEN    - path to a Maven 3.9+ executable
#    OFFLINE  - skip network downloads (deps will fail if missing)
# ----------------------------------------------------------------------------
set -euo pipefail
IFS=$'\n\t'

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# ---- logging helpers ---------------------------------------------------------
c_red='\033[0;31m'; c_grn='\033[0;32m'; c_yel='\033[0;33m'; c_dim='\033[2m'; c_off='\033[0m'
log()   { printf "  ${c_dim}·${c_off} %s\n" "$*"; }
ok()    { printf "${c_grn}✓${c_off} %s\n" "$*"; }
warn()  { printf "${c_yel}!${c_off} %s\n" "$*" >&2; }
fail()  { printf "${c_red}✗${c_off} %s\n" "$*" >&2; exit 1; }
hdr()   { printf "\n${c_yel}== %s ==${c_off}\n" "$*"; }

# ---- toolchain detection -----------------------------------------------------
detect_jdk() {
    if [[ -n "${JDK21:-}" && -x "$JDK21/bin/javac" ]]; then echo "$JDK21"; return; fi
    if [[ -x "$ROOT/.runtime/jdk-21.0.12.1+1/Contents/Home/bin/javac" ]]; then
        echo "$ROOT/.runtime/jdk-21.0.12.1+1/Contents/Home"; return
    fi
    if [[ -x "/usr/libexec/java_home" ]]; then
        local v; v=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
        [[ -n "$v" && -x "$v/bin/javac" ]] && { echo "$v"; return; }
    fi
    if command -v javac >/dev/null && javac -version 2>&1 | grep -q '"21'; then
        command -v javac | xargs dirname | xargs dirname; return
    fi
    return 1
}

detect_graalvm() {
    if [[ -n "${GRAALVM:-}" && -x "$GRAALVM/bin/native-image" ]]; then echo "$GRAALVM"; return; fi
    if [[ -x "$ROOT/.runtime/graalvm-community-openjdk-21.0.2+13.1/Contents/Home/bin/native-image" ]]; then
        echo "$ROOT/.runtime/graalvm-community-openjdk-21.0.2+13.1/Contents/Home"; return
    fi
    return 1
}

detect_maven() {
    if [[ -n "${MAVEN:-}" && -x "$MAVEN" ]]; then echo "$MAVEN"; return; fi
    local m
    for m in mvn /opt/homebrew/bin/mvn /usr/local/bin/mvn /tmp/apache-maven-3.9.16/bin/mvn; do
        command -v "$m" >/dev/null && { command -v "$m"; return; }
    done
    return 1
}

# ---- subcommands -------------------------------------------------------------
cmd_info() {
    hdr "toolchain"
    if JDK=$(detect_jdk); then
        log "JDK 21   : $JDK"; "$JDK/bin/java" -version 2>&1 | sed 's/^/           /'
    else
        warn "JDK 21 not found. Run: ./build.sh deps"
    fi
    if GRAAL=$(detect_graalvm); then
        log "GraalVM  : $GRAAL"; "$GRAAL/bin/native-image" --version 2>&1 | sed 's/^/           /' | head -2
    else
        log "GraalVM  : not installed (native target will be skipped)"
    fi
    if MVN=$(detect_maven); then
        log "Maven    : $MVN"; "$MVN" -v 2>&1 | grep -E "Java version:" | sed 's/^/           /'
    else
        warn "Maven not found. Run: ./build.sh deps"
    fi
    if [[ "$(uname -s)" == "Darwin" ]]; then
        log "clang    : $(command -v clang)"
        log "SDK      : $(xcrun --show-sdk-path 2>/dev/null)"
    else
        log "clang    : n/a (vibrancy dylib is macOS-only)"
    fi
    if [[ -f modern/dist/native/libDigitalVibrancy.dylib ]]; then
        log "vibrancy : modern/dist/native/libDigitalVibrancy.dylib ($(du -h modern/dist/native/libDigitalVibrancy.dylib | cut -f1))"
    else
        log "vibrancy : not built (run: ./build.sh vibrancy)"
    fi
    log "Workspace: $ROOT"
    log "OS       : $(uname -srm)"
}

cmd_deps() {
    hdr "dependencies"
    if detect_jdk >/dev/null; then ok "JDK 21 already present"
    else
        log "downloading Temurin JDK 21.0.12.1+1 (~190 MB)..."
        [[ -n "${OFFLINE:-}" ]] && fail "OFFLINE set but JDK missing"
        mkdir -p .runtime
        curl -sL -o .runtime/jdk.tar.gz \
            "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12%2B1/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.12_1.tar.gz"
        tar -xzf .runtime/jdk.tar.gz -C .runtime/
        rm .runtime/jdk.tar.gz
        ok "JDK 21 installed to .runtime/"
    fi
    if detect_graalvm >/dev/null; then ok "GraalVM already present"
    elif [[ "${WITH_GRAALVM:-0}" == "1" ]]; then
        log "downloading GraalVM CE 21.0.2 (~295 MB)..."
        [[ -n "${OFFLINE:-}" ]] && fail "OFFLINE set but GraalVM missing"
        curl -sL -o .runtime/graalvm.tar.gz \
            "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.2/graalvm-community-jdk-21.0.2_macos-aarch64_bin.tar.gz"
        tar -xzf .runtime/graalvm.tar.gz -C .runtime/
        rm .runtime/graalvm.tar.gz
        ok "GraalVM installed to .runtime/"
    else
        log "skip GraalVM download (set WITH_GRAALVM=1 to install)"
    fi
    if detect_maven >/dev/null; then ok "Maven already present"
    else
        log "downloading Maven 3.9.16 (~9 MB)..."
        [[ -n "${OFFLINE:-}" ]] && fail "OFFLINE set but Maven missing"
        mkdir -p /tmp
        curl -sL -o /tmp/maven.tar.gz \
            "https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz"
        tar -xzf /tmp/maven.tar.gz -C /tmp/
        rm /tmp/maven.tar.gz
        ok "Maven installed to /tmp/apache-maven-3.9.16/"
    fi
}

cmd_clean() {
    hdr "clean"
    rm -rf .build modern/dist source/target
    ok "removed .build, modern/dist, source/target"
}

cmd_jar() {
    hdr "jar"
    local JDK; JDK=$(detect_jdk) || fail "JDK 21 not found (run: ./build.sh deps)"
    mkdir -p .build/classes modern/dist
    # Resolve JNA (needed by Vibrancy.java). Populated by Maven; otherwise
    # we look for it under the standard local repository.
    local JNA_JAR="$HOME/.m2/repository/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar"
    [[ -f "$JNA_JAR" ]] || fail "jna-5.14.0.jar not found at $JNA_JAR — run './build.sh jlink' once so Maven downloads it"
    log "compiling modern UI overlay (target release 21)..."
    "$JDK/bin/javac" --release 21 -encoding UTF-8 \
        -cp "Digital.jar:modern/lib/flatlaf-3.7.jar:$JNA_JAR" \
        -d .build/classes \
        source/src/main/java/de/neemann/digital/gui/modern/ModernUI.java \
        source/src/main/java/de/neemann/digital/gui/modern/Vibrancy.java \
        source/src/main/java/de/neemann/gui/IconCreator.java \
        source/src/main/java/de/neemann/digital/gui/components/tree/SelectTree.java \
        source/src/main/java/de/neemann/digital/gui/Main.java \
        source/src/main/java/de/neemann/digital/gui/components/CircuitComponent.java \
        source/src/main/java/de/neemann/digital/gui/components/data/GraphComponent.java \
        source/src/main/java/de/neemann/digital/gui/components/table/ExpressionComponent.java \
        source/src/main/java/de/neemann/digital/gui/components/karnaugh/KarnaughMapComponent.java \
        source/src/main/java/de/neemann/digital/draw/shapes/InputShape.java \
        source/src/main/java/de/neemann/digital/draw/graphics/ColorScheme.java \
        source/src/main/java/de/neemann/digital/draw/graphics/GraphicSwing.java \
        source/src/main/java/de/neemann/digital/fsm/gui/FSMComponent.java
    cp Digital.jar modern/dist/Digital.jar
    "$JDK/bin/jar" uf modern/dist/Digital.jar -C .build/classes . -C modern/resources .
    cp modern/lib/flatlaf-3.7.jar modern/dist/

    # Rewrite the manifest so Main-Class and Class-Path are set correctly.
    # Done in Python so we don't need a maven build just for the manifest.
    log "rewriting MANIFEST.MF..."
    python3 - <<'PYMANIFEST'
import zipfile, pathlib, datetime
p = pathlib.Path('modern/dist/Digital.jar')
with zipfile.ZipFile(p) as z:
    entries = [(i, z.read(i)) for i in z.infolist()]
with zipfile.ZipFile(p, 'w', zipfile.ZIP_DEFLATED) as z:
    for i, data in entries:
        if i.filename == 'META-INF/MANIFEST.MF':
            ts = datetime.datetime.now().strftime('%Y-%m-%d')
            data = (b'Manifest-Version: 1.0\n'
                    b'Main-Class: de.neemann.digital.gui.Main\n'
                    b'Class-Path: flatlaf-3.7.jar\n'
                    b'Build-SCM-Revision: v0.31-modern\n'
                    b'Build-Time: ' + ts.encode() + b'\n\n')
        z.writestr(i, data)
PYMANIFEST
    ok "modern/dist/Digital.jar ($(du -h modern/dist/Digital.jar | cut -f1))"
}

cmd_vibrancy() {
    hdr "vibrancy"
    local SRC="$ROOT/modern/native/vibrancy/build.sh"
    [[ -f "$SRC" ]] || fail "missing $SRC"
    if [[ "$(uname -s)" == "Darwin" ]]; then
        "$SRC"
    else
        log "skipping (non-macOS host); placeholder kept in modern/dist/native/"
        mkdir -p modern/dist/native
    fi
}

cmd_iconset() {
    hdr "iconset"
    [[ -f icon.svg ]] || fail "icon.svg not found in $ROOT"
    rm -rf modern/assets/Digital.iconset
    mkdir -p modern/assets/Digital.iconset
    log "rendering icon.svg at standard macOS icon sizes..."
    for size in 16 32 64 128 256 512; do
        /usr/bin/qlmanage -t -s "$size" -o modern/assets/Digital.iconset icon.svg >/dev/null 2>&1
        if [[ -f modern/assets/Digital.iconset/icon.svg.png ]]; then
            mv modern/assets/Digital.iconset/icon.svg.png "modern/assets/Digital.iconset/icon_${size}x${size}.png"
        fi
    done
    # @2x retina variants
    for size in 32 64 128 256 512; do
        if [[ -f "modern/assets/Digital.iconset/icon_${size}x${size}.png" ]]; then
            cp "modern/assets/Digital.iconset/icon_${size}x${size}.png" \
               "modern/assets/Digital.iconset/icon_${size}x${size}@2x.png"
        fi
    done
    ok "rebuilt modern/assets/Digital.iconset/"
}

cmd_jlink() {
    hdr "jlink"
    local JDK MVN; JDK=$(detect_jdk) || fail "JDK 21 not found"
    MVN=$(detect_maven) || fail "Maven not found (run: ./build.sh deps)"
    cmd_vibrancy
    log "mvn -DskipTests package (one-time classpath + dependencies resolution)..."
    (cd source && JAVA_HOME="$JDK" "$MVN" -B -Dcheckstyle.skip -Denforcer.skip \
        -Djacoco.skip -Dmaven.javadoc.skip -Dgit.commit.id.describe=v0.31-modern package >/dev/null)
    ok "classes built"

    log "computing module set with jdeps..."
    local mods
    mods=$("$JDK/bin/jdeps" --print-module-deps --module-path "source/target/Digital.jar" \
        --add-modules=ALL-MODULE-PATH --ignore-missing-deps \
        source/target/classes 2>/dev/null | tr -d '\n' || true)
    [[ -z "$mods" ]] && mods="java.base,java.desktop,java.logging,java.xml,java.datatransfer,java.prefs,java.net.http,jdk.crypto.ec"
    log "modules: $mods"
    log "running jlink..."
    "$JDK/bin/jlink" \
        --module-path "$JDK/jmods" \
        --add-modules "$mods" \
        --strip-debug --no-man-pages --compress=zip-9 \
        --output modern/dist/runtime
    ok "modern/dist/runtime created (custom JRE, ~50 MB)"

    log "repackaging app + dependencies into modern/dist/Digital-app/..."
    mkdir -p modern/dist/Digital-app/lib
    cp -r modern/dist/runtime/* modern/dist/Digital-app/
    cp source/target/digital-1.0-SNAPSHOT.jar modern/dist/Digital-app/lib/
    cp modern/lib/flatlaf-3.7.jar modern/dist/Digital-app/lib/
    # Copy the native vibrancy dylib if it was built (macOS host only).
    if [[ -f modern/dist/native/libDigitalVibrancy.dylib ]]; then
        mkdir -p modern/dist/Digital-app/native
        cp modern/dist/native/libDigitalVibrancy.dylib modern/dist/Digital-app/native/
        log "bundled libDigitalVibrancy.dylib into Digital-app/native/"
    fi
    # Create launcher script
    cat > modern/dist/Digital-app/Digital <<'LAUNCHER'
#!/bin/sh
DIR="$(cd "$(dirname "$0")" && pwd)"
# Make the JVM find libDigitalVibrancy.dylib (macOS sidebar vibrancy).
export DYLD_LIBRARY_PATH="$DIR/native:${DYLD_LIBRARY_PATH:-}"
exec "$DIR/bin/java" -cp "$DIR/lib/*" de.neemann.digital.gui.Main "$@"
LAUNCHER
    chmod +x modern/dist/Digital-app/Digital
    ok "modern/dist/Digital-app ready (launcher + runtime + jars + native)"
}

_stage_jar_for_jpackage() {
    # Builds the staging dir jpackage needs: Digital.jar (with ModernUI overlay
    # classes and modern resources) PLUS all of its maven-resolved runtime
    # dependencies side by side. jpackage will fold the side-by-side jars into
    # the classpath automatically.
    # Always rebuild the overlay before packaging. Reusing an older jar here
    # makes a successful .app silently contain stale UI classes.
    cmd_jar
    [[ -f modern/lib/flatlaf-3.7.jar ]] || fail "modern/lib/flatlaf-3.7.jar missing"
    [[ -f source/target/digital-1.0-SNAPSHOT.jar ]] || cmd_jlink  # ensures classes
    local JDK; JDK=$(detect_jdk) || fail "JDK 21 not found"
    rm -rf .build/staging
    mkdir -p .build/staging
    log "staging the verified modern overlay jar..."
    local SHADED=.build/staging/Digital.jar
    # cmd_jar compiles the modern overlay classes (including the latest
    # sidebar/vibrancy changes) into modern/dist/Digital.jar. Rebuilding from
    # source/target/classes here silently discarded those classes and made a
    # successful DMG contain the old UI.
    cp modern/dist/Digital.jar "$SHADED"
    # Copy all runtime dependency jars into the staging dir so jpackage
    # wires them onto the classpath.
    log "collecting runtime dependency jars into staging..."
    cp modern/lib/flatlaf-3.7.jar .build/staging/
    local m2="${HOME}/.m2/repository"
    for j in \
        "$m2/com/formdev/flatlaf/3.7/flatlaf-3.7.jar" \
        "$m2/com/thoughtworks/xstream/xstream/1.4.21/xstream-1.4.21.jar" \
        "$m2/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar" \
        "$m2/org/slf4j/slf4j-simple/2.0.16/slf4j-simple-2.0.16.jar" \
        "$m2/org/json/json/20220924/json-20220924.jar" \
        "$m2/io/github/x-stream/mxparser/1.2.2/mxparser-1.2.2.jar" \
        "$m2/xmlpull/xmlpull/1.1.3.1/xmlpull-1.1.3.1.jar" \
        "$m2/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar"; do
        [[ -f "$j" ]] && cp "$j" .build/staging/
    done
    # Copy icon and licenses
    mkdir -p .build/staging/icons
    cp modern/assets/Digital.icns .build/staging/icons/ 2>/dev/null || true
    for f in LICENSE-Digital.txt LICENSE-FlatLaf.txt LICENSE-Lucide.txt; do
        cp "modern/dist/$f" .build/staging/ 2>/dev/null || true
    done
}

# Bundle libDigitalVibrancy.dylib into a jpackage-produced .app and wire
# the JVM's library path / dyld search so the dylib loads at launch.
# Idempotent and a no-op when the dylib is missing (non-macOS hosts).
_bundle_native_into_app() {
    local app="$1"
    local dylib="modern/dist/native/libDigitalVibrancy.dylib"
    [[ -f "$dylib" ]] || { log "no native dylib, skipping bundling"; return 0; }
    [[ -d "$app" ]] || fail "_bundle_native_into_app: $app not found"

    log "installing libDigitalVibrancy.dylib into $(basename "$app")/Contents/MacOS/..."
    local original_app="$app"
    local repair_app="${app%.app}-repair.app"
    rm -rf "$repair_app"
    # jpackage can mark its freshly-created bundle with macOS managed
    # attributes that reject an in-place copy. Repair a clean duplicate when
    # that happens, then swap it back after signing.
    if ! cp "$dylib" "$app/Contents/MacOS/libDigitalVibrancy.dylib" 2>/dev/null; then
        log "copying app-image to a clean bundle before native injection..."
        cp -R "$app" "$repair_app"
        app="$repair_app"
        cp "$dylib" "$app/Contents/MacOS/libDigitalVibrancy.dylib"
    fi
    # Sign the dylib before signing the containing app. The app bundle is
    # sealed below, after all injected files and plist changes are complete.
    if command -v codesign >/dev/null; then
        codesign --force --sign - "$app/Contents/MacOS/libDigitalVibrancy.dylib" \
            >/dev/null 2>&1 || true
    fi

    # Patch Info.plist to add DYLD_LIBRARY_PATH so dyld finds the dylib
    # during java -cp launch. Uses plutil so we don't need Python here.
    # plutil requires the parent dict to exist before inserting a child
    # key, so we create LSEnvironment first when it's missing.
    local plist="$app/Contents/Info.plist"
    if command -v plutil >/dev/null && [[ -f "$plist" ]]; then
        if ! plutil -extract LSEnvironment.DYLD_LIBRARY_PATH raw "$plist" >/dev/null 2>&1; then
            plutil -insert LSEnvironment -dictionary "$plist" 2>/dev/null || true
            plutil -insert LSEnvironment.DYLD_LIBRARY_PATH \
                -string '@executable_path/' "$plist" 2>/dev/null || true
        fi
    fi

    # jpackage signs the app before this function runs. Copying the native
    # library and changing Info.plist afterwards invalidates that signature,
    # which makes Finder report the misleading "no permission ... (null)".
    # Re-sign the complete bundle only after every modification is finished.
    if command -v codesign >/dev/null; then
        codesign --force --deep --sign - "$app" >/dev/null 2>&1 \
            || fail "could not ad-hoc sign $app after bundling native code"
        codesign --verify --deep --strict "$app" >/dev/null 2>&1 \
            || fail "invalid code signature in $app"
    fi
    if [[ "$app" != "$original_app" ]]; then
        local invalid_app="${original_app%.app}-invalid-latest.app"
        rm -rf "$invalid_app"
        mv "$original_app" "$invalid_app"
        cp -R "$app" "$original_app"
        rm -rf "$app"
    fi
    ok "native vibrancy bundled"
}

cmd_app() {
    hdr "app"
    local JDK; JDK=$(detect_jdk) || fail "JDK 21 not found"
    cmd_vibrancy
    _stage_jar_for_jpackage
    [[ -f modern/assets/Digital.icns ]] || {
        warn "Digital.icns missing; running iconset + iconutil..."
        cmd_iconset
        iconutil -c icns modern/assets/Digital.iconset -o modern/assets/Digital.icns
    }
    rm -rf modern/dist/Digital.app
    log "jpackage --type app-image..."
    set +e
    "$JDK/bin/jpackage" \
        --type app-image \
        --name "Digital" \
        --input .build/staging \
        --main-jar Digital.jar \
        --main-class de.neemann.digital.gui.Main \
        --icon modern/assets/Digital.icns \
        --app-version "1.31.0" \
        --vendor "Digital" \
        --description "Digital — a digital logic designer and circuit simulator" \
        --copyright "© 2026 Digital contributors" \
        --java-options "-Dfile.encoding=UTF-8" \
        --java-options "-Djna.library.path=@executable_path/" \
        --java-options "--add-opens=java.desktop/java.awt=ALL-UNNAMED" \
        --java-options "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED" \
        --java-options "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED" \
        --java-options "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED" \
        --dest modern/dist
    local jpackage_status=$?
    set -e
    # Some macOS/JDK combinations return non-zero while trying to adjust the
    # signature of the bundled runtime, even though the app-image is complete.
    # Keep going only when the expected bundle exists; the final signing below
    # is authoritative after the native library is injected.
    if [[ "$jpackage_status" -ne 0 && ! -d modern/dist/Digital.app ]]; then
        fail "jpackage failed and did not produce modern/dist/Digital.app"
    elif [[ "$jpackage_status" -ne 0 ]]; then
        warn "jpackage returned $jpackage_status; continuing with final bundle signing"
    fi
    _bundle_native_into_app modern/dist/Digital.app
    if [[ -d modern/dist/Digital.app ]]; then
        local size; size=$(du -sh modern/dist/Digital.app | cut -f1)
        ok "modern/dist/Digital.app ($size) — double-click to launch"
    else
        fail "jpackage did not produce modern/dist/Digital.app"
    fi
}

cmd_dmg() {
    hdr "dmg"
    local JDK; JDK=$(detect_jdk) || fail "JDK 21 not found"
    # jpackage --type dmg does not expose the generated .app on this macOS
    # toolchain, so the native vibrancy dylib cannot be injected afterward.
    # Build a real app-image first, bundle the dylib, then create the DMG from
    # that verified app-image.
    # cmd_app leaves the verified app-image in modern/dist/Digital.app.
    # Keep it while hdiutil reads it, then leave the app-image available for
    # package inspection alongside the DMG.
    cmd_app
    rm -f modern/dist/Digital-*.dmg
    log "hdiutil create --format UDZO..."
    hdiutil create \
        -volname "Digital" \
        -srcfolder modern/dist/Digital.app \
        -ov \
        -format UDZO \
        modern/dist/Digital-1.31.0.dmg >/dev/null
    if [[ -f modern/dist/Digital-1.31.0.dmg ]]; then
        local size; size=$(du -sh modern/dist/Digital-1.31.0.dmg | cut -f1)
        ok "modern/dist/Digital-1.31.0.dmg ($size) — drag to /Applications"
    else
        warn "jpackage dmg produced, check modern/dist/"
        ls modern/dist/*.dmg 2>/dev/null
    fi
}

cmd_native() {
    hdr "native"
    local JDK GRAAL MVN
    JDK=$(detect_jdk)    || fail "JDK 21 not found"
    GRAAL=$(detect_graalvm) || fail "GraalVM not found (set GRAALVM=... or run WITH_GRAALVM=1 ./build.sh deps)"
    MVN=$(detect_maven)  || fail "Maven not found"
    log "mvn -DskipTests package (build classpath)..."
    (cd source && JAVA_HOME="$JDK" "$MVN" -B -Dcheckstyle.skip -Denforcer.skip \
        -Djacoco.skip -Dmaven.javadoc.skip -Dgit.commit.id.describe=v0.31-modern package >/dev/null)
    log "native-image (this takes ~1 min, produces ~54 MB binary)..."
    local CP="source/target/digital-1.0-SNAPSHOT.jar"
    CP+=":$HOME/.m2/repository/com/formdev/flatlaf/3.7/flatlaf-3.7.jar"
    CP+=":$HOME/.m2/repository/com/thoughtworks/xstream/xstream/1.4.21/xstream-1.4.21.jar"
    CP+=":$HOME/.m2/repository/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar"
    CP+=":$HOME/.m2/repository/org/slf4j/slf4j-simple/2.0.16/slf4j-simple-2.0.16.jar"
    CP+=":$HOME/.m2/repository/org/json/json/20220924/json-20220924.jar"
    CP+=":$HOME/.m2/repository/io/github/x-stream/mxparser/1.2.2/mxparser-1.2.2.jar"
    CP+=":$HOME/.m2/repository/xmlpull/xmlpull/1.1.3.1/xmlpull-1.1.3.1.jar"
    JAVA_HOME="$GRAAL" "$GRAAL/bin/native-image" \
        -cp "$CP" \
        --no-fallback \
        -H:+ReportExceptionStackTraces \
        --initialize-at-build-time=org.slf4j.simple.SimpleLogger \
        -o modern/dist/digital \
        de.neemann.digital.gui.Main
    ok "modern/dist/digital ($(du -h modern/dist/digital | cut -f1) standalone binary)"
}

cmd_all() {
    cmd_vibrancy
    cmd_jar
    cmd_app
    [[ -x "$(detect_graalvm 2>/dev/null || true)" ]] && cmd_native || warn "skipping native (GraalVM not installed)"
    [[ -x "$(detect_maven 2>/dev/null || true)" ]] && cmd_jlink  || warn "skipping jlink (Maven not installed)"
}

# ---- entrypoint --------------------------------------------------------------
usage() { sed -n '3,28p' "$0" | sed 's/^# \?//'; }
case "${1:-}" in
    info|jar|vibrancy|native|jlink|iconset|app|dmg|clean|deps|all|"") cmd_${1:-info} ;;
    -h|--help|help) usage ;;
    *) usage; fail "unknown target: $1" ;;
esac
