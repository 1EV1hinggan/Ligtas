@echo off
rem Publishes the console to https://ligtas-eb260.web.app . Double-click after you change anything in public\ (index.html, css\style.css or js\*.js).
cd /d "%~dp0"
call firebase deploy --only hosting
echo.
echo Done. Open https://ligtas-eb260.web.app and press Ctrl+Shift+R.
pause
