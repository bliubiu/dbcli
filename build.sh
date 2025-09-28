#!/bin/bash

# 多数据库指标收集工具 dbcli 构建脚本
# 版本: 1.0.0
# 作者: dbcli开发团队

set -e  # 遇到错误立即退出

echo "=========================================="
echo "多数据库指标收集工具 dbcli v1.0.0"
echo "构建脚本"
echo "=========================================="

# 获取脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "项目目录: $SCRIPT_DIR"
echo "构建时间: $(date)"
echo ""

# 检查Java环境
echo "🔍 检查Java环境..."
if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
    echo "✅ 使用JAVA_HOME: $JAVA_HOME"
else
    JAVA_CMD="java"
    echo "⚠️  未设置JAVA_HOME，使用系统默认Java"
fi

if ! command -v $JAVA_CMD &> /dev/null; then
    echo "❌ 错误: 找不到Java运行环境"
    echo "请安装Java 8或更高版本，或设置JAVA_HOME环境变量"
    exit 1
fi

JAVA_VERSION=$($JAVA_CMD -version 2>&1 | head -n 1 | cut -d'"' -f2)
echo "✅ Java版本: $JAVA_VERSION"
echo ""

# 检查Maven环境
echo "🔍 检查Maven环境..."
if ! command -v mvn &> /dev/null; then
    echo "❌ 错误: 找不到Maven"
    echo "请安装Maven 3.6或更高版本"
    echo "下载地址: https://maven.apache.org/download.cgi"
    exit 1
fi

MVN_VERSION=$(mvn -version | head -n 1 | cut -d' ' -f3)
echo "✅ Maven版本: $MVN_VERSION"
echo ""

# 检查网络连接（可选）
echo "🔍 检查网络连接..."
if ping -c 1 repo1.maven.org &> /dev/null; then
    echo "✅ 网络连接正常"
else
    echo "⚠️  网络连接异常，可能影响依赖下载"
fi
echo ""

# 清理旧的构建文件
echo "🧹 清理旧的构建文件..."
if [ -d "target" ]; then
    rm -rf target
    echo "✅ 清理target目录"
fi

# 创建必要的目录
echo "📁 创建必要的目录..."
mkdir -p configs metrics reports log lib
echo "✅ 目录创建完成"
echo ""

# 开始构建
echo "🔨 开始Maven构建..."
echo "执行命令: mvn clean package -DskipTests"
echo ""

# 执行Maven构建
if mvn clean package -DskipTests; then
    echo ""
    echo "🎉 构建成功！"
    
    # 检查生成的JAR文件
    JAR_FILE="target/dbcli-1.0.0.jar"
    if [ -f "$JAR_FILE" ]; then
        JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
        echo "✅ 生成的JAR文件: $JAR_FILE ($JAR_SIZE)"
        
        # 验证JAR文件
        if $JAVA_CMD -jar "$JAR_FILE" --help &> /dev/null; then
            echo "✅ JAR文件验证通过"
        else
            echo "⚠️  JAR文件验证失败，可能存在问题"
        fi
    else
        echo "❌ 未找到生成的JAR文件"
        exit 1
    fi
    
    echo ""
    echo "=========================================="
    echo "构建完成！"
    echo "=========================================="
    echo ""
    echo "📦 可执行文件: target/dbcli-1.0.0.jar"
    echo "📁 项目目录: $SCRIPT_DIR"
    echo ""
    echo "🚀 使用方法:"
    echo "  ./dbcli.sh --help              # 查看帮助信息"
    echo "  ./dbcli.sh --template          # 生成配置模板"
    echo "  ./dbcli.sh --encrypt           # 加密配置文件"
    echo "  ./dbcli.sh --test              # 测试数据库连接"
    echo "  ./dbcli.sh -c configs -m metrics  # 执行指标收集"
    echo ""
    echo "📋 部署步骤:"
    echo "1. 将数据库驱动JAR文件放入 lib/ 目录"
    echo "2. 配置数据库连接信息到 configs/ 目录"
    echo "3. 配置监控指标到 metrics/ 目录"
    echo "4. 运行 ./dbcli.sh 开始监控"
    echo ""
    echo "📚 更多信息请查看 README.md 和 examples/ 目录"
    
else
    echo ""
    echo "❌ 构建失败！"
    echo ""
    echo "🔧 常见问题解决方案:"
    echo "1. 检查网络连接，确保能访问Maven中央仓库"
    echo "2. 清理Maven本地仓库: rm -rf ~/.m2/repository"
    echo "3. 检查Java版本，确保使用Java 8或更高版本"
    echo "4. 检查Maven版本，确保使用Maven 3.6或更高版本"
    echo ""
    exit 1
fi
