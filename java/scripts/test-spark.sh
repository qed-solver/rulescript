#!/bin/bash
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backend="$here/src/main/java/org/qed/Backends/Spark"

# Spark 4 runs on JDK 17 or 21 (JDK 25 works too, with a warning from Hadoop).
if [ -z "${SPARK_JAVA_HOME:-}" ]; then
    for candidate in /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home /usr/lib/jvm/java-21-openjdk-amd64; do
        if [ -x "$candidate/bin/java" ]; then
            SPARK_JAVA_HOME="$candidate"
            break
        fi
    done
fi

cd "$here"
./scripts/check-no-hardcoding-spark.sh
if [ "${SKIP_CODEGEN:-0}" != "1" ]; then
    ./mvnw -q compile exec:java@spark-codegen 2>&1 | grep -v WARNING
fi

cd "$backend"
MVN="$(ls -d "$HOME"/.m2/wrapper/dists/apache-maven-*-bin/*/apache-maven-*/bin/mvn 2>/dev/null | head -1)"
JAVA_HOME="${SPARK_JAVA_HOME:-${JAVA_HOME:-}}" "${MVN:-mvn}" -q -B test "$@"
