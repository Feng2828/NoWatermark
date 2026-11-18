@echo off
rem Gradle start up script for Windows. Usage: gradlew.bat assembleRelease
setlocal
set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if defined JAVA_HOME (
  set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
  set JAVACMD=java.exe
)
if not exist "%CLASSPATH%" (
  echo gradle-wrapper.jar not found; run: gradle wrapper --gradle-version 8.7 >&2
  exit /b 1
)
"%JAVACMD%" %JAVA_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
