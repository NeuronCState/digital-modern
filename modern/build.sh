#!/bin/zsh
set -euo pipefail
ROOT="${0:A:h:h}"
cd "$ROOT"
JDK="$ROOT/.runtime/jdk-21.0.12.1+1/Contents/Home"
mkdir -p .build/classes modern/dist
"$JDK/bin/javac" --release 8 -encoding UTF-8 -cp 'Digital.jar:modern/lib/flatlaf-3.7.jar' -d .build/classes source/src/main/java/de/neemann/digital/gui/modern/ModernUI.java source/src/main/java/de/neemann/gui/IconCreator.java source/src/main/java/de/neemann/digital/gui/components/tree/SelectTree.java source/src/main/java/de/neemann/digital/gui/Main.java
cp Digital.jar modern/dist/Digital.jar
"$JDK/bin/jar" uf modern/dist/Digital.jar -C .build/classes . -C modern/resources .
cp modern/lib/flatlaf-3.7.jar modern/dist/
python3 - <<'PYMANIFEST'
import zipfile,pathlib
p=pathlib.Path('modern/dist/Digital.jar')
with zipfile.ZipFile(p) as z:
    entries=[(i,z.read(i)) for i in z.infolist()]
with zipfile.ZipFile(p,'w',zipfile.ZIP_DEFLATED) as z:
    for i,data in entries:
        if i.filename == 'META-INF/MANIFEST.MF':
            data=b'Manifest-Version: 1.0\nMain-Class: de.neemann.digital.gui.Main\nClass-Path: flatlaf-3.7.jar\nBuild-SCM-Revision: v0.31-modern\nBuild-Time: 2026-09-09\n\n'
        z.writestr(i,data)
PYMANIFEST
