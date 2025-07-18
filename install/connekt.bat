@echo off
setlocal enabledelayedexpansion

rem === CONFIG ===
set IMAGE=ghcr.io/amplicode/connekt:0.2.10
set ENV_FILE=
set ENV_NAME=
set "ENV_PARAM_ARGS="
set "SCRIPT_ARGS="

rem === PARSE ARGS ===
:parse_args
if "%~1"=="" goto :after_args

if /i "%~1"=="-env" (
set "ENV_FILE=%~2"
shift
shift
goto :parse_args
)

if /i "%~1"=="-envname" (
set "ENV_NAME=%~2"
shift
shift
goto :parse_args
)

if /i "%~1"=="-envparams" (
shift
:envparam_loop
if "%~1"=="" goto :parse_args
if "%~1"=="-env" goto :parse_args
if "%~1"=="-envname" goto :parse_args
if "%~1"=="-envparams" goto :parse_args
set "ENV_PARAM_ARGS=!ENV_PARAM_ARGS! --env-param %~1"
shift
goto :envparam_loop
)

rem default: treat as script
set "SCRIPT_ARGS=!SCRIPT_ARGS! %~1"
shift
goto :parse_args

:after_args

rem === Check if Docker image exists locally ===
docker image inspect "%IMAGE%" >nul 2>&1
if errorlevel 1 (
echo 🔄 Docker image %IMAGE% not found locally. Pulling...
docker pull "%IMAGE%"
)

rem === Prepare ENV_FILE mount ===
set "ENV_FILE_MOUNT="
set "ENV_FILE_ARG="
if not "%ENV_FILE%"=="" (
for %%F in ("%ENV_FILE%") do set "ENV_FILE_PATH=%%~fF"
set "ENV_FILE_MOUNT=-v !ENV_FILE_PATH!:/connekt/scripts/connekt.env.json"
set "ENV_FILE_ARG=--env-file=scripts/connekt.env.json"
)

rem === Prepare ENV_NAME arg ===
set "ENV_NAME_ARG="
if not "%ENV_NAME%"=="" (
set "ENV_NAME_ARG=--env-name=%ENV_NAME%"
)

rem === Run each script ===
for %%S in (%SCRIPT_ARGS%) do (
for %%F in ("%%~fS") do (
set "DIR=%%~dpF"
set "FILE=%%~nxF"
echo 🚀 Running !FILE! ...
docker run --rm ^
--add-host=host.docker.internal:host-gateway ^
-v "!DIR!:/connekt/scripts" ^
%ENV_FILE_MOUNT% ^
%IMAGE% ^
%ENV_NAME_ARG% ^
%ENV_FILE_ARG% ^
--script=scripts/!FILE! ^
%ENV_PARAM_ARGS%
)
)

exit /b 0
