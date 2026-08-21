@echo off
chcp 65001 > nul
title Actualizador Automatico a GitHub - OropSOS (Brayan Jesus Oropeza Acuña)
color 0A

echo =======================================================================
echo          ACTUALIZADOR AUTOMATICO A GITHUB - OROPSOS
echo =======================================================================
echo.

cd /d "%~dp0"

set "GIT_CMD=C:\Program Files\Git\cmd\git.exe"

if not exist "%GIT_CMD%" (
    where git >nul 2>nul
    if %errorlevel% equ 0 (
        set "GIT_CMD=git"
    ) else (
        echo [ERROR] No se encontro Git instalado en C:\Program Files\Git\cmd\git.exe
        pause
        exit /b
    )
)

:: Inicializar git si no existe
if not exist ".git" (
    echo Inicializando repositorio Git...
    "%GIT_CMD%" init
    "%GIT_CMD%" branch -M main
    "%GIT_CMD%" remote add origin https://github.com/brayanoropeza/OropSOS.git
)

:: Verificar y asegurar remoto
"%GIT_CMD%" remote set-url origin https://github.com/brayanoropeza/OropSOS.git

echo Agregando archivos modificados (icono de Escudo Rojo, Alerta Sismica SASMEX)...
"%GIT_CMD%" add .

set /p commit_msg="Escribe una breve descripcion del cambio (o presiona ENTER para usar por defecto): "
if "%commit_msg%"=="" (
    set commit_msg=Actualizacion OropSOS: Icono Escudo Rojo con O blanca y Alerta Sismica SASMEX
)

echo Guardando cambios (Commit)...
"%GIT_CMD%" commit -m "%commit_msg%"

echo.
echo Subiendo cambios a https://github.com/brayanoropeza/OropSOS (push)...
"%GIT_CMD%" push -u origin main

echo.
echo =======================================================================
echo  ¡EXITO! Los cambios fueron subidos correctamente a GitHub.
echo  GitHub Actions ha comenzado a compilar tu APK de OropSOS.
echo =======================================================================
echo.
pause
