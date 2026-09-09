#!/bin/zsh
set -euo pipefail
ROOT="${0:A:h:h}"
cd "$ROOT"
JDK="$ROOT/.runtime/jdk-21.0.12.1+1/Contents/Home"
mkdir -p .build/tests
"$JDK/bin/javac" -encoding UTF-8 -cp 'modern/dist/Digital.jar:modern/lib/flatlaf-3.7.jar' -d .build/tests modern/tests/ModernSmokeTest.java
"$JDK/bin/java" -Djava.awt.headless=true -Ddigital.reduceMotion=true -cp '.build/tests:modern/dist/Digital.jar:modern/lib/flatlaf-3.7.jar' ModernSmokeTest
