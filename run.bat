@echo off
rem Paleidzia simuliacija, pvz.: run.bat --seed 7 --difficulty 4
setlocal
cd /d "%~dp0"
call build.bat || exit /b 1
java -cp out\classes bc.App %*
