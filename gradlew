#!/usr/bin/env sh
# Gradle start up script (POSIX). Usage: ./gradlew assembleRelease
set -e
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=java
fi
if [ ! -f "$CLASSPATH" ]; then
  echo "gradle-wrapper.jar not found; run: gradle wrapper --gradle-version 8.7" >&2
  exit 1
fi
exec "$JAVACMD" $JAVA_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
