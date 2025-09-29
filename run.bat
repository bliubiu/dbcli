@echo off
REM 简化版 dbcli 运行脚本
REM 支持命令行模式运行和启动Web管理界面

chcp 65001 >nul

setlocal ENABLEDELAYEDEXPANSION

echo ==========================================
echo dbcli 简化运行脚本
echo ==========================================

REM 检查参数
if "%~1"=="-h" goto :SHOW_HELP
if "%~1"=="--help" goto :SHOW_HELP

REM 检查是否为Web管理模式
set "WEB_MODE="
for %%A in (%*) do (
    if /I "%%~A"=="--web" set "WEB_MODE=1"
    if /I "%%~A"=="--web-management" set "WEB_MODE=1"
    if /I "%%~A"=="-w" set "WEB_MODE=1"
)

if defined WEB_MODE (
    echo 启动Web管理界面...
    call dbcli.bat %*
) else (
    echo 编译并运行...
    REM 简化执行流程
    call mvn -q -DskipTests clean compile
    if errorlevel 1 (
        echo 编译失败
        exit /b 1
    )
    
    call mvn package -DskipTests -q
    if errorlevel 1 (
        echo 打包失败
        exit /b 1
    )
    
    call dbcli.bat %*
)

goto :EOF

:SHOW_HELP
echo.
echo 用法: run.bat [选项]
echo.
echo 选项:
echo   -w, --web, --web-management  启动Web管理界面
echo   -h, --help                   显示此帮助信息
echo.
echo 示例:
echo   run.bat                      # 编译并运行命令行模式
echo   run.bat -w                   # 启动Web管理界面
echo.