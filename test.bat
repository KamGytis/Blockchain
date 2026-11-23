@echo off
setlocal
cd /d "%~dp0"
call build.bat || exit /b 1
java -cp "out\classes;out\test" bc.Tests
