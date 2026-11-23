@echo off
rem Kompiliuoja programa (out\classes) ir testus (out\test). Reikia JDK 17+.
setlocal
cd /d "%~dp0"
if exist out\classes rmdir /s /q out\classes
if exist out\test rmdir /s /q out\test
javac -encoding UTF-8 -d out\classes src\bc\*.java src\bc\core\*.java src\bc\hash\*.java src\bc\model\*.java src\bc\sim\*.java || exit /b 1
javac -encoding UTF-8 -cp out\classes -d out\test test\bc\*.java || exit /b 1
