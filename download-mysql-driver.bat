@echo off
echo 正在下载MySQL驱动...

:: 创建lib目录（如果不存在）
if not exist "lib" mkdir lib

:: 下载MySQL驱动 8.0.33
echo 下载MySQL Connector/J 8.0.33...
curl -L -o "lib/mysql-connector-j-8.0.33.jar" "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar"

if exist "lib/mysql-connector-j-8.0.33.jar" (
    echo ✅ MySQL驱动下载成功！
    echo 文件位置: lib/mysql-connector-j-8.0.33.jar
    dir lib\mysql-connector-j-8.0.33.jar
) else (
    echo ❌ MySQL驱动下载失败！
    echo 请手动下载MySQL Connector/J并放置到lib目录
    echo 下载地址: https://dev.mysql.com/downloads/connector/j/
)

echo.
echo 下载完成后，请重新运行数据库连接测试
pause