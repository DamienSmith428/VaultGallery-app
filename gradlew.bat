@rem Gradle startup script for Windows
@echo off
set JAVACMD=java
if not "%JAVA_HOME%"=="" set JAVACMD=%JAVA_HOME%\bin\java
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
"%JAVACMD%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
