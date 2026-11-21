@echo off
setlocal
cd /d "%~dp0"
if defined JAVA_HOME (
  "%JAVA_HOME%\bin\java.exe" -ea -jar "development-client.jar" --developer-mode --debug
) else (
  java -ea -jar "development-client.jar" --developer-mode --debug
)
if errorlevel 1 (
  echo.
  echo Install Java 17 or newer from adoptium.net and try again.
  echo For Jagex Accounts, follow the RuneLite instructions linked in README.md.
  pause
)
