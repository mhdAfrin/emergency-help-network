@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0application\scripts\launch.ps1" -Mode server
if errorlevel 1 pause
