#!/bin/sh
# Gradle wrapper script for Unix
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
GRADLE_HOME=""
JAVACMD="${JAVA_HOME}/bin/java"
if [ -z "${JAVA_HOME}" ]; then JAVACMD="java"; fi
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH="${APP_HOME}/gradle/wrapper/gradle-wrapper.jar"
exec "$JAVACMD" $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
