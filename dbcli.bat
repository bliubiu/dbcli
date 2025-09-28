@echo off
REM dbcli Windows 启动脚本（精简版，无颜色、无表情符）
REM 版本: 1.0.0

setlocal ENABLEDELAYEDEXPANSION

REM 设置控制台编码为UTF-8（可选）
chcp 65001 >nul

REM 切换到脚本目录
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

echo ==========================================
echo dbcli Windows 启动器 v1.0.0
echo ==========================================
echo 项目目录: %SCRIPT_DIR%
echo 启动时间: %date% %time%
echo.

REM 帮助快速通道：拦截 -h/--help/?
set "_ARG1=%~1"
if /I "%_ARG1%"=="-h" goto :PRINT_HELP
if /I "%_ARG1%"=="--help" goto :PRINT_HELP
if "%_ARG1%"=="/?" goto :PRINT_HELP

REM 检测是否为测试模式（--test 或 -t）
set "TEST_MODE="
for %%A in (%*) do (
    if /I "%%~A"=="--test" set "TEST_MODE=1"
    if /I "%%~A"=="-t" set "TEST_MODE=1"
)

REM 检查Java环境
if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    echo 使用JAVA_HOME: %JAVA_HOME%
) else (
    set "JAVA_CMD=java"
    echo 未设置JAVA_HOME，使用系统默认Java
)

REM 检查Java是否可用
"%JAVA_CMD%" -version >nul 2>&1
if errorlevel 1 (
    echo 找不到Java运行环境，请安装JDK或设置JAVA_HOME
    exit /b 1
)

REM 如未编译则编译
if not exist "target\classes" (
    echo 项目未编译，开始编译...
    call mvn -q clean compile
    if errorlevel 1 (
        echo 项目编译失败
        exit /b 1
    )
    echo 项目编译成功
    echo.
)

REM 创建必要目录
for %%d in (configs metrics reports logs lib) do (
    if not exist "%%d" mkdir "%%d"
)

REM 优先使用可执行JAR文件（包含所有依赖）
set "JAR_FILE="
for %%f in (target\dbcli-*.jar) do (
    if not "%%f"=="target\original-dbcli-*.jar" (
        set "JAR_FILE=%%f"
        goto :found_jar
    )
)
:found_jar

REM 如果找到JAR文件，优先使用JAR运行（测试模式和正常模式都使用）
if defined JAR_FILE (
    echo 使用JAR文件: "%JAR_FILE%"
    if defined TEST_MODE (
        echo 测试模式：使用打包JAR运行（包含所有依赖）
    )
    "%JAVA_CMD%" -Dfile.encoding=UTF-8 -jar "%JAR_FILE%" %*
    set "EXIT_CODE=%ERRORLEVEL%"
    exit /b !EXIT_CODE!
)

REM 如果没有JAR文件，使用类路径运行（包含Maven依赖）
echo 未找到可执行JAR，使用类路径运行
set "CLASSPATH=target\classes"

REM 添加Maven依赖到classpath
for /f "tokens=*" %%i in ('mvn dependency:build-classpath -Dmdep.outputFile=temp_classpath.txt -q 2^>nul ^&^& type temp_classpath.txt 2^>nul') do (
    set "MAVEN_CLASSPATH=%%i"
)
if exist temp_classpath.txt del temp_classpath.txt

REM 添加lib目录下的JAR文件
for %%f in (lib\*.jar) do (
    if exist "%%f" set "CLASSPATH=!CLASSPATH!;%%f"
)

REM 合并Maven依赖和lib依赖
if defined MAVEN_CLASSPATH (
    set "CLASSPATH=!CLASSPATH!;!MAVEN_CLASSPATH!"
)

if defined TEST_MODE (
    echo 测试模式：使用类路径运行，包含Maven依赖和lib目录
)

"%JAVA_CMD%" -Dfile.encoding=UTF-8 -cp "!CLASSPATH!" com.dbcli.DbCliApplication %*

set "EXIT_CODE=%ERRORLEVEL%"

echo.
if %EXIT_CODE% EQU 0 (
    echo dbcli 执行完成（成功）
) else (
    echo dbcli 执行失败（退出代码: %EXIT_CODE%）
)
exit /b %EXIT_CODE%

:PRINT_HELP
setlocal DISABLEDELAYEDEXPANSION
call :_pl "=========================================="
call :_pl "dbcli 帮助"
call :_pl "=========================================="
call :_pl "usage: dbcli"
call :_pl "多数据库指标收集工具"
call :_pl ""
call :_pl "选项:"
call :_pl "  -c--config [路径]    指定配置文件路径（默认：configs/）"
call :_pl "  --dry-run          仅验证配置，不执行实际操作"
call :_pl "  -e--encrypt        加密配置文件中的敏感信息"
call :_pl "  -f--format [格式]    输出格式：excel/html/both（默认：excel）"
call :_pl "  -h--help           显示帮助信息"
call :_pl "  -m--metrics [路径]   指标文件路径（默认：metrics/）"
call :_pl "  -o--output [路径]    输出路径（默认：reports/）"
call :_pl "  -p--threads [数量]   并发线程数（默认：7）"
call :_pl "  -t--test           测试数据库连接"
call :_pl "  --template         生成配置文件模板"
call :_pl "  --version          显示版本信息"
endlocal
exit /b 0

:_pl
set "line=%~1"
if "%line%"=="" (echo.) else echo %line%
exit /b
