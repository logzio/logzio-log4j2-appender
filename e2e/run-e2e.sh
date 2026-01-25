#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

if [ -z "$LOGZIO_TOKEN" ]; then
    echo "ERROR: LOGZIO_TOKEN environment variable is required"
    exit 1
fi

if [ -z "$ENV_ID" ]; then
    echo "ERROR: ENV_ID environment variable is required"
    exit 1
fi

echo "=== E2E Test Configuration ==="
echo "ENV_ID: $ENV_ID"
echo "Project root: $PROJECT_ROOT"
echo "=============================="

echo "Installing logzio-log4j2-appender to local Maven repository..."
cd "$PROJECT_ROOT"

VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
JAR_FILE="$PROJECT_ROOT/target/logzio-log4j2-appender-${VERSION}.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "ERROR: JAR file not found at $JAR_FILE"
    echo "Please run 'mvn package -DskipTests' first"
    exit 1
fi

mvn install:install-file \
    -Dfile="$JAR_FILE" \
    -DgroupId=io.logz.log4j2 \
    -DartifactId=logzio-log4j2-appender \
    -Dversion="$VERSION" \
    -Dpackaging=jar \
    -DgeneratePom=false \
    --batch-mode \
    --no-transfer-progress

echo "Running E2E test with logzio-appender version: $VERSION"
cd "$SCRIPT_DIR"
mvn compile exec:java -Dlogzio-appender.version="$VERSION" --batch-mode --no-transfer-progress

echo "E2E test completed successfully"

