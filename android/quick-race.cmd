@echo off
py -3 "%~dp0tools\quick_race.py" %*
if not errorlevel 1 exit /b 0
if "%~1"=="" pause
exit /b 1
