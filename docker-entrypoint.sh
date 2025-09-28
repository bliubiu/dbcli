#!/bin/bash
set -e

# Docker容器启动脚本
echo "========================================="
echo "DBCLI 容器启动中..."
echo "========================================="

# 显示环境信息
echo "Java版本: $(java -version 2>&1 | head -n 1)"
echo "工作目录: $(pwd)"
echo "用户信息: $(whoami)"
echo "时区设置: $(date)"

# 检查必要的目录
echo "检查目录结构..."
for dir in configs metrics lib logs reports temp; do
    if [ ! -d "$dir" ]; then
        echo "创建目录: $dir"
        mkdir -p "$dir"
    fi
done

# 检查配置文件
echo "检查配置文件..."
config_count=$(find configs -name "*.yml" -o -name "*.yaml" | wc -l)
if [ "$config_count" -eq 0 ]; then
    echo "警告: 未找到配置文件，请确保configs目录包含数据库配置文件"
fi

# 检查指标文件
echo "检查指标文件..."
metrics_count=$(find metrics -name "*.yml" -o -name "*.yaml" | wc -l)
if [ "$metrics_count" -eq 0 ]; then
    echo "警告: 未找到指标文件，请确保metrics目录包含指标定义文件"
fi

# 检查数据库驱动
echo "检查数据库驱动..."
driver_count=$(find lib -name "*.jar" 2>/dev/null | wc -l)
if [ "$driver_count" -eq 0 ]; then
    echo "警告: 未找到数据库驱动，请确保lib目录包含必要的JDBC驱动"
else
    echo "找到 $driver_count 个数据库驱动文件"
fi

# 检查加密密钥
if [ -z "$DBCLI_SM4_KEY" ]; then
    echo "警告: 未设置DBCLI_SM4_KEY环境变量，将使用默认密钥（不安全）"
else
    echo "已设置加密密钥"
fi

# 设置JVM参数
if [ -z "$JAVA_OPTS" ]; then
    JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC"
fi

echo "JVM参数: $JAVA_OPTS"

# 设置应用参数
if [ $# -eq 0 ]; then
    # 默认参数
    APP_ARGS="-c configs -m metrics -o reports -f excel"
else
    # 使用传入的参数
    APP_ARGS="$@"
fi

echo "应用参数: $APP_ARGS"

# 创建启动日志
echo "$(date): 容器启动" >> logs/container.log

# 信号处理函数
cleanup() {
    echo "接收到停止信号，正在优雅关闭..."
    echo "$(date): 容器停止" >> logs/container.log
    exit 0
}

# 注册信号处理
trap cleanup SIGTERM SIGINT

echo "========================================="
echo "启动DBCLI应用..."
echo "Web管理界面: http://localhost:${DBCLI_WEB_PORT:-8080}"
echo "========================================="

# 启动应用
exec java $JAVA_OPTS -jar app.jar $APP_ARGS