#!/usr/bin/env bash
set -e

# Use Java 21 LTS installed on macOS
if [ -d "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
fi

echo "=========================================================="
echo " Running with Java: $JAVA_HOME"
echo "=========================================================="

TARGET=${1:-"java"}

case "$TARGET" in
  "java")
    echo ">> Running Modern Java 21 Module (ecommerce-search-java)..."
    mvn exec:java -pl ecommerce-search-java
    ;;
  "kotlin")
    echo ">> Running Kotlin Module (ecommerce-search-kotlin)..."
    mvn exec:java -pl ecommerce-search-kotlin
    ;;
  "all")
    echo ">> 1. Running Modern Java 21 Module..."
    mvn exec:java -pl ecommerce-search-java
    echo ""
    echo ">> 2. Running Kotlin Module..."
    mvn exec:java -pl ecommerce-search-kotlin
    ;;
  *)
    echo "Usage: ./run.sh [java|kotlin|all]"
    exit 1
    ;;
esac
