@echo off
setlocal enabledelayedexpansion

:: PaiCLI 启动脚本 - 可从任意目录调用
:: 用法: 将 bin/ 目录加入 PATH，然后直接运行 paicli
::
:: 例如:
::   set PATH=%%PATH%%;D:\Code\MyProject\paicli\bin
::   paicli

set "SCRIPT_DIR=%~dp0"
pushd "%SCRIPT_DIR%.."
set "PROJECT_DIR=%CD%"
popd

set "JAR=%PROJECT_DIR%\target\paicli-1.0-SNAPSHOT.jar"

if not exist "%JAR%" (
    echo Error: PaiCLI jar not found at %JAR%
    echo Please run 'mvn clean package' first.
    pause
    exit /b 1
)

java -jar "%JAR%" %*
endlocal
