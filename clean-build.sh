#!/bin/bash

# 设置UTF-8编码
export LANG=zh_CN.UTF-8

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "========================================"
echo "清理旧构建文件并重新构建 dbcli 项目"
echo "========================================"

# 检查Java环境
echo -e "${BLUE}检查Java环境...${NC}"
if ! command -v java &> /dev/null; then
    echo -e "${RED}错误: 未找到Java环境，请确保已安装Java 8或更高版本${NC}"
    exit 1
fi

# 检查Maven环境
echo -e "${BLUE}检查Maven环境...${NC}"
if command -v mvn &> /dev/null; then
    echo -e "${GREEN}Maven环境正常${NC}"
    USE_MAVEN=true
else
    echo -e "${YELLOW}警告: 未找到Maven，将使用javac直接编译${NC}"
    USE_MAVEN=false
fi

# 1. 清理旧的构建文件
echo ""
echo -e "${BLUE}步骤 1/5: 清理旧构建文件...${NC}"

if [ -d "target" ]; then
    echo "删除 target 目录..."
    rm -rf target
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}target 目录已删除${NC}"
    else
        echo -e "${RED}警告: 无法删除target目录${NC}"
    fi
fi

if [ -d "classes" ]; then
    echo "删除 classes 目录..."
    rm -rf classes
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}classes 目录已删除${NC}"
    else
        echo -e "${RED}警告: 无法删除classes目录${NC}"
    fi
fi

# 清理日志文件（可选）
echo "清理旧日志文件..."
if [ -d "logs" ]; then
    find logs -name "*.log" -delete 2>/dev/null
    find logs -name "*.err" -delete 2>/dev/null
    find logs -name "*.txt" -delete 2>/dev/null
fi

# 清理临时文件
find . -maxdepth 1 -name "*.class" -delete 2>/dev/null

echo -e "${GREEN}旧构建文件清理完成${NC}"

# 2. 创建必要的目录结构
echo ""
echo -e "${BLUE}步骤 2/5: 创建目录结构...${NC}"

DIRS=("target/classes" "configs" "metrics" "reports" "logs" "lib")

for dir in "${DIRS[@]}"; do
    if [ ! -d "$dir" ]; then
        mkdir -p "$dir"
        if [ $? -eq 0 ]; then
            echo "创建目录: $dir"
        else
            echo -e "${RED}错误: 无法创建目录 $dir${NC}"
        fi
    fi
done

# 创建子日志目录
LOG_DIRS=("logs/connection" "logs/execution" "logs/performance" "logs/error")

for dir in "${LOG_DIRS[@]}"; do
    if [ ! -d "$dir" ]; then
        mkdir -p "$dir"
        echo "创建日志目录: $dir"
    fi
done

echo -e "${GREEN}目录结构创建完成${NC}"

# 3. 检查依赖库
echo ""
echo -e "${BLUE}步骤 3/5: 检查依赖库...${NC}"

REQUIRED_LIBS=("snakeyaml" "slf4j-api" "logback-classic" "logback-core" "poi" "poi-ooxml")

echo "检查lib目录下的JAR文件:"
MISSING_LIBS=0

if ls lib/*.jar 1> /dev/null 2>&1; then
    for jar in lib/*.jar; do
        echo "  找到: $(basename "$jar")"
    done
else
    echo -e "${YELLOW}警告: lib目录下未找到JAR文件${NC}"
    echo -e "${YELLOW}请确保以下依赖库已放置在lib目录下:${NC}"
    for lib in "${REQUIRED_LIBS[@]}"; do
        echo "  - $lib-*.jar"
    done
    MISSING_LIBS=1
fi

# 4. 编译项目
echo ""
echo -e "${BLUE}步骤 4/5: 编译项目...${NC}"

if [ "$USE_MAVEN" = true ]; then
    echo "使用Maven编译..."
    mvn clean compile
    if [ $? -ne 0 ]; then
        echo -e "${RED}Maven编译失败${NC}"
        exit 1
    fi
    echo -e "${GREEN}Maven编译成功${NC}"
else
    echo "使用javac直接编译..."
    
    # 查找所有Java源文件
    echo "查找Java源文件..."
    find src -name "*.java" > temp_sources.txt
    
    # 设置classpath
    CLASSPATH="lib/*"
    
    # 编译
    echo "开始编译..."
    javac -cp "$CLASSPATH" -d target/classes @temp_sources.txt
    if [ $? -ne 0 ]; then
        echo -e "${RED}javac编译失败${NC}"
        rm -f temp_sources.txt
        exit 1
    fi
    
    rm -f temp_sources.txt
    echo -e "${GREEN}javac编译成功${NC}"
fi

# 5. 验证构建结果
echo ""
echo -e "${BLUE}步骤 5/5: 验证构建结果...${NC}"

if [ ! -f "target/classes/com/dbcli/DbCliApplication.class" ]; then
    echo -e "${RED}错误: 主类文件未找到${NC}"
    exit 1
fi

echo "检查编译后的类文件:"
CLASS_COUNT=$(find target/classes -name "*.class" | wc -l)

if [ $CLASS_COUNT -gt 0 ]; then
    echo -e "${GREEN}找到 $CLASS_COUNT 个编译后的类文件${NC}"
else
    echo -e "${RED}错误: 未找到编译后的类文件${NC}"
    exit 1
fi

# 测试运行
echo ""
echo -e "${BLUE}测试运行...${NC}"
java -cp "target/classes:lib/*" com.dbcli.DbCliApplication --help > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}警告: 程序运行测试失败，可能缺少依赖库${NC}"
    if [ $MISSING_LIBS -eq 1 ]; then
        echo -e "${YELLOW}请检查lib目录下的依赖库是否完整${NC}"
    fi
else
    echo -e "${GREEN}程序运行测试通过${NC}"
fi

# 构建成功
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}构建完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "使用方法:"
echo "  java -cp \"target/classes:lib/*\" com.dbcli.DbCliApplication [选项]"
echo ""
echo "或使用shell脚本:"
echo "  ./dbcli.sh [选项]"
echo ""
echo "常用选项:"
echo "  --help          显示帮助信息"
echo "  --template      生成配置模板"
echo "  --test          测试数据库连接"
echo "  --encrypt       加密配置文件"
echo ""