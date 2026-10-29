@echo off
rem Doble clic: instala FocusGuard en el celular conectado y deja los permisos configurados.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\instalar.ps1" %*
