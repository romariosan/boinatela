@echo off
REM Nunca use o titulo CHIP RADAR como comando. So o node.exe + radar.js.
set PUPPETEER_CACHE_DIR=
set PUPPETEER_TMP_DIR=
cd /d "%~dp0"
"C:\Program Files\nodejs\node.exe" radar.js > radar-saida.log 2> radar-erro.log
