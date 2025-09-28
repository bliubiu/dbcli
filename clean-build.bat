@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

echo ========================================
echo 清理旧构建文件并重新构建 dbcli 项目
echo ========================================

:: 设置颜色输出
set "GREEN=[32m"
set "RED=[31m"
set "YELLOW=[33m"
set "BLUE=[34m"
set "RESET=[0m"

:: 检查Java环境
echo %BLUE%检查Java环境...%RESET%
java -version >nul 2>&1
if errorlevel 1 (
    echo %RED%错误: 未找到Java环境，请确保已安装Java 8或更高版本%RESET%
    pause
    exit /b 1
)

:: 检查Maven环境
echo %BLUE%检查Maven环境...%RESET%
mvn -version >nul 2>&1
if errorlevel 1 (
    echo %YELLOW%警告: 未找到Maven，将使用javac直接编译%RESET%
    set USE_MAVEN=false
) else (
    echo %GREEN%Maven环境正常%RESET%
    set USE_MAVEN=true
)

:: 1. 清理旧的构建文件
echo.
echo %BLUE%步骤 1/5: 清理旧构建文件...%RESET%

if exist target (
    echo 删除 target 目录...
    rmdir /s /q target
    if errorlevel 1 (
        echo %RED%警告: 无法完全删除target目录，可能有文件被占用%RESET%
    ) else (
        echo %GREEN%target 目录已删除%RESET%
    )
)

if exist classes (
    echo 删除 classes 目录...
    rmdir /s /q classes
    if errorlevel 1 (
        echo %RED%警告: 无法完全删除classes目录%RESET%
    ) else (
        echo %GREEN%classes 目录已删除%RESET%
    )
)

:: 清理日志文件（可选）
echo 清理旧日志文件...
if exist logs (
    for %%f in (logs\*.log logs\*.err logs\*.txt) do (
        if exist "%%f" (
            echo 删除日志文件: %%f
            del /q "%%f" 2>nul
        )
    )
)

:: 清理临时文件
if exist *.class (
    echo 删除根目录下的.class文件...
    del /q *.class 2>nul
)

echo %GREEN%旧构建文件清理完成%RESET%

:: 2. 创建必要的目录结构
echo.
echo %BLUE%步骤 2/5: 创建目录结构...%RESET%

set DIRS=target\classes configs metrics reports logs lib

for %%d in (%DIRS%) do (
    if not exist "%%d" (
        mkdir "%%d" 2>nul
        if errorlevel 1 (
            echo %RED%错误: 无法创建目录 %%d%RESET%
        ) else (
            echo 创建目录: %%d
        )
    )
)

:: 创建子日志目录
set LOG_DIRS=logs\connection logs\execution logs\performance logs\error

for %%d in (%LOG_DIRS%) do (
    if not exist "%%d" (
        mkdir "%%d" 2>nul
        echo 创建日志目录: %%d
    )
)

echo %GREEN%目录结构创建完成%RESET%

:: 3. 检查依赖库
echo.
echo %BLUE%步骤 3/5: 检查依赖库...%RESET%

set REQUIRED_LIBS=snakeyaml slf4j-api logback-classic logback-core poi poi-ooxml

echo 检查lib目录下的JAR文件:
set MISSING_LIBS=0
for %%f in (lib\*.jar) do (
    echo   找到: %%~nxf
)

if not exist lib\*.jar (
    echo %YELLOW%警告: lib目录下未找到JAR文件%RESET%
    echo %YELLOW%请确保以下依赖库已放置在lib目录下:%RESET%
    for %%l in (%REQUIRED_LIBS%) do (
        echo   - %%l-*.jar
    )
    set MISSING_LIBS=1
)

:: 4. 编译项目
echo.
echo %BLUE%步骤 4/5: 编译项目...%RESET%

if "%USE_MAVEN%"=="true" (
    echo 使用Maven编译...
    call mvn clean compile
    if errorlevel 1 (
        echo %RED%Maven编译失败%RESET%
        goto :error
    )
    echo %GREEN%Maven编译成功%RESET%
) else (
    echo 使用javac直接编译...
    
    :: 查找所有Java源文件
    echo 查找Java源文件...
    dir /s /b src\*.java > temp_sources.txt
    
    :: 设置classpath
    set "CLASSPATH=lib\*"
    
    :: 编译
    echo 开始编译...
    javac -cp "%CLASSPATH%" -d target\classes @temp_sources.txt
    if errorlevel 1 (
        echo %RED%javac编译失败%RESET%
        del temp_sources.txt 2>nul
        goto :error
    )
    
    del temp_sources.txt 2>nul
    echo %GREEN%javac编译成功%RESET%
)

:: 5. 验证构建结果
echo.
echo %BLUE%步骤 5/5: 验证构建结果...%RESET%

if not exist target\classes\com\dbcli\DbCliApplication.class (
    echo %RED%错误: 主类文件未找到%RESET%
    goto :error
)

echo 检查编译后的类文件:
for /r target\classes %%f in (*.class) do (
    set /a CLASS_COUNT+=1
)

if !CLASS_COUNT! gtr 0 (
    echo %GREEN%找到 !CLASS_COUNT! 个编译后的类文件%RESET%
) else (
    echo %RED%错误: 未找到编译后的类文件%RESET%
    goto :error
)

:: 测试运行
echo.
echo %BLUE%测试运行...%RESET%
java -cp "target\classes;lib\*" com.dbcli.DbCliApplication --help >nul 2>&1
if errorlevel 1 (
    echo %YELLOW%警告: 程序运行测试失败，可能缺少依赖库%RESET%
    if %MISSING_LIBS%==1 (
        echo %YELLOW%请检查lib目录下的依赖库是否完整%RESET%
    )
) else (
    echo %GREEN%程序运行测试通过%RESET%
)

:: 生成构建报告
echo.
echo %BLUE%生成构建报告...%RESET%

set END_TIME=%time%
set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
set TIMESTAMP=%TIMESTAMP:/=%
set TIMESTAMP=%TIMESTAMP::=%

set REPORT_FILE=build_report_%TIMESTAMP%.txt

echo # dbcli 构建报告 > "%REPORT_FILE%"
echo 生成时间: %date% %time% >> "%REPORT_FILE%"
echo 构建时间: %START_TIME% - %END_TIME% >> "%REPORT_FILE%"
echo. >> "%REPORT_FILE%"
echo ## 环境信息 >> "%REPORT_FILE%"
echo Java版本: !JAVA_VERSION! >> "%REPORT_FILE%"
if "%USE_MAVEN%"=="true" (
    echo Maven版本: 已安装 >> "%REPORT_FILE%"
) else (
    echo Maven: 未使用 >> "%REPORT_FILE%"
)
echo. >> "%REPORT_FILE%"
echo ## 构建结果 >> "%REPORT_FILE%"
echo 编译状态: 成功 >> "%REPORT_FILE%"
echo 类文件数: !CLASS_COUNT! >> "%REPORT_FILE%"
echo. >> "%REPORT_FILE%"
echo ## 项目信息 >> "%REPORT_FILE%"
echo 项目名称: dbcli >> "%REPORT_FILE%"
echo 项目描述: 多数据库指标收集工具 >> "%REPORT_FILE%"
echo 支持数据库: Oracle, MySQL, PostgreSQL, 达梦 >> "%REPORT_FILE%"

if exist "%REPORT_FILE%" (
    echo %GREEN%构建报告已生成: %REPORT_FILE%%RESET%
) else (
    echo %YELLOW%构建报告生成失败%RESET%
)

:: 构建成功
echo.
echo %GREEN%========================================%RESET%
echo %GREEN%构建完成！%RESET%
echo %GREEN%========================================%RESET%
echo.
echo 构建报告: %REPORT_FILE%
echo.
echo 使用方法:
echo   java -cp "target\classes;lib\*" com.dbcli.DbCliApplication [选项]
echo.
echo 或使用批处理文件:
echo   dbcli.bat [选项]
echo.
echo 常用选项:
echo   --help          显示帮助信息
echo   --template      生成配置模板
echo   --test          测试数据库连接
echo   --encrypt       加密配置文件
echo.

goto :end

:error
echo.
echo %RED%========================================%RESET%
echo %RED%构建失败！%RESET%
echo %RED%========================================%RESET%
echo.
echo 请检查以下问题:
echo 1. Java环境是否正确安装
echo 2. 源代码是否完整
echo 3. lib目录下的依赖库是否完整
echo 4. 是否有权限写入target目录
echo.
pause
exit /b 1

:end
echo 按任意键退出...
pause >nul