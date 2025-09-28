@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

:: 脚本参数处理
set "CLEAN_ONLY=false"
set "SKIP_TEST=false"
set "VERBOSE=false"

:parse_args
if "%~1"=="" goto :args_done
if /i "%~1"=="--clean-only" set "CLEAN_ONLY=true"
if /i "%~1"=="--skip-test" set "SKIP_TEST=true"
if /i "%~1"=="--verbose" set "VERBOSE=true"
if /i "%~1"=="-v" set "VERBOSE=true"
if /i "%~1"=="--help" goto :show_help
if /i "%~1"=="-h" goto :show_help
shift
goto :parse_args

:args_done

echo ========================================
echo 增强版 dbcli 项目构建脚本
echo ========================================

:: 设置颜色输出
set "GREEN=[32m"
set "RED=[31m"
set "YELLOW=[33m"
set "BLUE=[34m"
set "CYAN=[36m"
set "RESET=[0m"

:: 记录开始时间
set START_TIME=%time%

:: 显示构建信息
echo %CYAN%构建配置:%RESET%
echo   清理模式: %CLEAN_ONLY%
echo   跳过测试: %SKIP_TEST%
echo   详细输出: %VERBOSE%
echo   开始时间: %START_TIME%
echo.

:: 环境检查
call :check_environment
if errorlevel 1 exit /b 1

:: 清理构建文件
call :clean_build_files
if errorlevel 1 exit /b 1

if "%CLEAN_ONLY%"=="true" (
    echo %GREEN%清理完成，退出%RESET%
    goto :end
)

:: 创建目录结构
call :create_directories
if errorlevel 1 exit /b 1

:: 检查依赖
call :check_dependencies
if errorlevel 1 exit /b 1

:: 编译项目
call :compile_project
if errorlevel 1 exit /b 1

:: 验证构建
call :verify_build
if errorlevel 1 exit /b 1

:: 运行测试
if "%SKIP_TEST%"=="false" (
    call :run_tests
)

:: 生成构建报告
call :generate_build_report

goto :end

:: ============ 函数定义 ============

:check_environment
echo %BLUE%检查构建环境...%RESET%

:: 检查Java
java -version >nul 2>&1
if errorlevel 1 (
    echo %RED%错误: 未找到Java环境%RESET%
    echo 请安装Java 8或更高版本
    exit /b 1
)

for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION=%%i
    set JAVA_VERSION=!JAVA_VERSION:"=!
)
echo Java版本: !JAVA_VERSION!

:: 检查Maven
mvn -version >nul 2>&1
if errorlevel 1 (
    echo Maven: 未安装 ^(将使用javac^)
    set USE_MAVEN=false
) else (
    for /f "tokens=3" %%i in ('mvn -version 2^>^&1 ^| findstr "Apache Maven"') do (
        set MAVEN_VERSION=%%i
    )
    echo Maven版本: !MAVEN_VERSION!
    set USE_MAVEN=true
)

echo %GREEN%环境检查完成%RESET%
exit /b 0

:clean_build_files
echo %BLUE%清理构建文件...%RESET%

set CLEAN_DIRS=target classes
set CLEAN_FILES=*.class temp_*.txt

for %%d in (%CLEAN_DIRS%) do (
    if exist "%%d" (
        echo 删除目录: %%d
        rmdir /s /q "%%d" 2>nul
        if exist "%%d" (
            echo %YELLOW%警告: 无法完全删除 %%d%RESET%
        )
    )
)

for %%f in (%CLEAN_FILES%) do (
    if exist "%%f" (
        echo 删除文件: %%f
        del /q "%%f" 2>nul
    )
)

:: 清理日志文件（保留最近3个）
if exist logs (
    echo 清理旧日志文件...
    for /f "skip=3 delims=" %%f in ('dir /b /o-d logs\*.log 2^>nul') do (
        if "%VERBOSE%"=="true" echo 删除旧日志: logs\%%f
        del /q "logs\%%f" 2>nul
    )
)

echo %GREEN%清理完成%RESET%
exit /b 0

:create_directories
echo %BLUE%创建目录结构...%RESET%

set MAIN_DIRS=target\classes configs metrics reports logs lib
set LOG_DIRS=logs\connection logs\execution logs\performance logs\error

for %%d in (%MAIN_DIRS%) do (
    if not exist "%%d" (
        mkdir "%%d" 2>nul
        if errorlevel 1 (
            echo %RED%错误: 无法创建目录 %%d%RESET%
            exit /b 1
        )
        if "%VERBOSE%"=="true" echo 创建: %%d
    )
)

for %%d in (%LOG_DIRS%) do (
    if not exist "%%d" (
        mkdir "%%d" 2>nul
        if "%VERBOSE%"=="true" echo 创建: %%d
    )
)

echo %GREEN%目录结构创建完成%RESET%
exit /b 0

:check_dependencies
echo %BLUE%检查项目依赖...%RESET%

set REQUIRED_LIBS=snakeyaml slf4j-api logback-classic logback-core poi poi-ooxml
set FOUND_LIBS=0
set MISSING_LIBS=0

if exist lib\*.jar (
    echo 发现的JAR文件:
    for %%f in (lib\*.jar) do (
        set /a FOUND_LIBS+=1
        if "%VERBOSE%"=="true" echo   %%~nxf
    )
    echo 共找到 !FOUND_LIBS! 个JAR文件
) else (
    echo %YELLOW%警告: lib目录下未找到JAR文件%RESET%
    set /a MISSING_LIBS+=1
)

:: 检查源代码文件
set JAVA_FILES=0
for /r src %%f in (*.java) do (
    set /a JAVA_FILES+=1
)

if !JAVA_FILES! gtr 0 (
    echo 找到 !JAVA_FILES! 个Java源文件
) else (
    echo %RED%错误: 未找到Java源文件%RESET%
    exit /b 1
)

if !MISSING_LIBS! gtr 0 (
    echo %YELLOW%建议检查依赖库的完整性%RESET%
)

echo %GREEN%依赖检查完成%RESET%
exit /b 0

:compile_project
echo %BLUE%编译项目...%RESET%

if "%USE_MAVEN%"=="true" (
    echo 使用Maven编译...
    if "%VERBOSE%"=="true" (
        mvn clean compile
    ) else (
        mvn clean compile -q
    )
    if errorlevel 1 (
        echo %RED%Maven编译失败%RESET%
        exit /b 1
    )
) else (
    echo 使用javac编译...
    
    :: 生成源文件列表
    dir /s /b src\*.java > temp_sources.txt
    
    :: 编译
    set "CLASSPATH=lib\*"
    if "%VERBOSE%"=="true" (
        javac -cp "!CLASSPATH!" -d target\classes -verbose @temp_sources.txt
    ) else (
        javac -cp "!CLASSPATH!" -d target\classes @temp_sources.txt
    )
    
    if errorlevel 1 (
        echo %RED%javac编译失败%RESET%
        del temp_sources.txt 2>nul
        exit /b 1
    )
    
    del temp_sources.txt 2>nul
)

echo %GREEN%编译完成%RESET%
exit /b 0

:verify_build
echo %BLUE%验证构建结果...%RESET%

:: 检查主类
if not exist target\classes\com\dbcli\DbCliApplication.class (
    echo %RED%错误: 主类文件未找到%RESET%
    exit /b 1
)

:: 统计类文件
set CLASS_COUNT=0
for /r target\classes %%f in (*.class) do (
    set /a CLASS_COUNT+=1
)

echo 编译生成 !CLASS_COUNT! 个类文件

:: 检查关键类文件
set KEY_CLASSES=DbCliApplication CommandLineProcessor ConfigLoader MetricsCollectionService
set MISSING_CLASSES=0

for %%c in (%KEY_CLASSES%) do (
    if not exist target\classes\com\dbcli\*%%c.class (
        echo %YELLOW%警告: 未找到关键类 %%c%RESET%
        set /a MISSING_CLASSES+=1
    )
)

if !MISSING_CLASSES! gtr 0 (
    echo %YELLOW%发现 !MISSING_CLASSES! 个缺失的关键类%RESET%
)

echo %GREEN%构建验证完成%RESET%
exit /b 0

:run_tests
echo %BLUE%运行基本测试...%RESET%

:: 测试帮助信息
echo 测试帮助信息显示...
java -cp "target\classes;lib\*" com.dbcli.DbCliApplication --help >nul 2>&1
if errorlevel 1 (
    echo %YELLOW%警告: 帮助信息测试失败%RESET%
) else (
    echo %GREEN%帮助信息测试通过%RESET%
)

:: 测试模板生成
echo 测试模板生成功能...
java -cp "target\classes;lib\*" com.dbcli.DbCliApplication --template >nul 2>&1
if errorlevel 1 (
    echo %YELLOW%警告: 模板生成测试失败%RESET%
) else (
    echo %GREEN%模板生成测试通过%RESET%
)

echo %GREEN%基本测试完成%RESET%
exit /b 0

:generate_build_report
echo %BLUE%生成构建报告...%RESET%

set END_TIME=%time%

:: 生成简单的时间戳文件名
set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
set TIMESTAMP=%TIMESTAMP:/=%
set TIMESTAMP=%TIMESTAMP::=%

set REPORT_FILE=build_report_%TIMESTAMP%.txt

echo # dbcli 构建报告 > "%REPORT_FILE%"
echo 构建时间: %date% %START_TIME% - %END_TIME% >> "%REPORT_FILE%"
echo. >> "%REPORT_FILE%"
echo ## 环境信息 >> "%REPORT_FILE%"
echo Java版本: %JAVA_VERSION% >> "%REPORT_FILE%"
if "%USE_MAVEN%"=="true" (
    echo Maven版本: %MAVEN_VERSION% >> "%REPORT_FILE%"
) else (
    echo Maven: 未使用 >> "%REPORT_FILE%"
)
echo. >> "%REPORT_FILE%"
echo ## 构建结果 >> "%REPORT_FILE%"
echo 编译状态: 成功 >> "%REPORT_FILE%"
echo 类文件数: %CLASS_COUNT% >> "%REPORT_FILE%"
echo JAR依赖数: %FOUND_LIBS% >> "%REPORT_FILE%"
echo. >> "%REPORT_FILE%"
echo ## 构建统计 >> "%REPORT_FILE%"
echo 源文件数: %JAVA_FILES% >> "%REPORT_FILE%"
if !MISSING_CLASSES! gtr 0 (
    echo 缺失关键类: %MISSING_CLASSES% >> "%REPORT_FILE%"
) else (
    echo 关键类检查: 通过 >> "%REPORT_FILE%"
)

if exist "%REPORT_FILE%" (
    echo 构建报告已生成: %REPORT_FILE%
    echo %GREEN%构建报告生成完成%RESET%
) else (
    echo %RED%构建报告生成失败%RESET%
)
exit /b 0

:show_help
echo dbcli 增强构建脚本
echo.
echo 用法: %~nx0 [选项]
echo.
echo 选项:
echo   --clean-only    仅清理构建文件，不进行编译
echo   --skip-test     跳过基本功能测试
echo   --verbose, -v   显示详细输出信息
echo   --help, -h      显示此帮助信息
echo.
echo 示例:
echo   %~nx0                    # 完整构建
echo   %~nx0 --clean-only       # 仅清理
echo   %~nx0 --verbose          # 详细输出
echo   %~nx0 --skip-test -v     # 跳过测试且详细输出
echo.
exit /b 0

:end
set END_TIME=%time%
echo.
echo %GREEN%========================================%RESET%
echo %GREEN%构建脚本执行完成%RESET%
echo %GREEN%========================================%RESET%
echo 开始时间: %START_TIME%
echo 结束时间: %END_TIME%
echo.
echo 使用方法:
echo   java -cp "target\classes;lib\*" com.dbcli.DbCliApplication [选项]
echo   或: dbcli.bat [选项]
echo.
pause