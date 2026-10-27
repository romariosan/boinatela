@echo off
REM Nunca use o titulo CHIP CONSULTOR como comando.
set PUPPETEER_CACHE_DIR=
set PUPPETEER_TMP_DIR=
cd /d "%~dp0"
"C:\Program Files\nodejs\node.exe" consultor.js > consultor-saida.log 2> consultor-erro.log
