@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0application\scripts\project.ps1" build %*
if errorlevel 1 pause
