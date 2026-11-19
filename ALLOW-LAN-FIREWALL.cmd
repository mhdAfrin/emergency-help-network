@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0application\scripts\lan\Firewall.ps1" %*
pause
