@echo off
REM ===========================================================================
REM House Rent System - Windows one-click launcher
REM
REM IMPORTANT: this file is intentionally ASCII-only and CRLF. cmd.exe parses
REM batch files with the OEM code page (936/GBK on zh-CN). Non-ASCII comments in
REM a UTF-8 file get decoded as garbage, and bytes inside the garbage can break
REM the REM line into executable commands - which is exactly what caused the
REM launcher to flash and close. All Chinese messages come from scripts\hrent.ps1
REM (UTF-8 BOM, parsed by PowerShell, not by cmd).
REM
REM   start.cmd                build if needed, start backend + serve built web
REM   start.cmd dev            backend + frontend dev server (hot reload)
REM   start.cmd run            run backend in foreground (Ctrl+C to stop)
REM   start.cmd stop           stop backend and frontend
REM   start.cmd status         status + health check (backend / frontend)
REM   start.cmd logs -f        follow backend log (--web for frontend log)
REM   start.cmd build-web      build frontend into hrent-web\dist
REM   start.cmd help           full usage
REM
REM Double-clicking keeps the window open so the result stays readable.
REM Set HRENT_NO_PAUSE=1 to disable that (CI / automation).
REM ===========================================================================
setlocal EnableExtensions

set "RC=0"
set "PAUSE_AT_END="

REM Keep the window open only for the no-argument run (the double-click case).
REM Command-line invocations (status/stop/logs/...) stay non-blocking so they
REM remain scriptable in CI. HRENT_NO_PAUSE=1 forces silent exit as well.
REM Note: %CMDCMDLINE% is NOT used here - with the doubled quotes that
REM Explorer produces it gets truncated when assigned via set "VAR=...".
if "%~1"=="" set "PAUSE_AT_END=1"
if defined HRENT_NO_PAUSE set "PAUSE_AT_END="

set "PS1=%~dp0scripts\hrent.ps1"
if not exist "%PS1%" (
    echo [hrent] scripts\hrent.ps1 not found.
    echo [hrent] Run start.cmd from the project root, keep the scripts folder.
    set "RC=1"
    goto :summary
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%" %*
set "RC=%ERRORLEVEL%"

:summary
echo.
REM status/stop/logs return 1 by design (e.g. "not running"). Do not label that
REM as a launcher failure - only start/run/build failures need the full dossier.
set "QUERY_CMD="
if /i "%~1"=="status" set "QUERY_CMD=1"
if /i "%~1"=="stop"   set "QUERY_CMD=1"
if /i "%~1"=="logs"   set "QUERY_CMD=1"
if not "%RC%"=="0" (
    if defined QUERY_CMD (
        echo [hrent] exit code %RC%.
    ) else (
        echo [hrent] FAILED - exit code %RC%.
        echo [hrent] Reason is printed above.
        echo [hrent] Log file : %~dp0logs\hrent.log
        echo [hrent] Error    : %~dp0logs\hrent.error.log
        echo [hrent] Try      : start.cmd logs
    )
) else (
    echo [hrent] OK - exit code 0.
)

if defined PAUSE_AT_END (
    echo.
    if "%RC%"=="0" (
        echo [hrent] Service runs in background. Close this window when ready.
    ) else (
        echo [hrent] Read the message above, then press any key to close.
    )
    pause >nul
)

exit /b %RC%
