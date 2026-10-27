@echo off
REM Nunca use o titulo CHIP SISTEMA como comando.
set PUPPETEER_CACHE_DIR=
set PUPPETEER_TMP_DIR=
cd /d "%~dp0"
"C:\Program Files\nodejs\node.exe" sistema.js > sistema-saida.log 2> sistema-erro.log
