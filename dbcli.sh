#!/bin/bash

# 多数据库指标收集工具 dbcli 启动脚本 (Linux/macOS)
# 版本: 1.0.0
# 作者: dbcli开发团队
# 使用方法: ./dbcli.sh [参数]

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# 显示帮助信息
show_help() {
    echo "=========================================="
    echo "多数据库指标收集工具 dbcli v1.0.0"
    echo "=========================================="
    echo ""
    echo "使用方法: ./dbcli.sh [选项]"
    echo ""
    echo "选项:"
    echo "  -h, --help              显示此帮助信息"
    echo "  -c, --config <path>     指定配置文件路径 (默认: configs/)"
    echo "  -m, --metrics <path>    指定指标文件路径 (默认: metrics/)"
    echo "  -o, --output <path>     指定输出路径 (默认: reports/)"
    echo "  -f, --format <format>   指定输出格式 (excel|html|both, 默认: both)"
    echo "  -t, --template          生成配置模板文件"
    echo "  -e, --encrypt           加密配置文件中的敏感信息"
    echo "  --test                  测试数据库连接"
    echo "  --parallel <num>        设置并发线程数 (默认: 4)"
    echo "  --timeout <seconds>     设置连接超时时间 (默认: 30)"
    echo "  --log-level <level>     设置日志级别 (DEBUG|INFO|WARN|ERROR, 默认: INFO)"
    echo "  --dry-run               模拟运行，不执行实际查询"
    echo ""
    echo "示例:"
    echo "  ./dbcli.sh                                    # 使用默认配置运行"
    echo "  ./dbcli.sh --template                         # 生成配置模板"
    echo "  ./dbcli.sh --test                             # 测试数据库连接"
    echo "  ./dbcli.sh -c myconfigs -m mymetrics          # 指定配置和指标目录"
    echo "  ./dbcli.sh -f excel -o /tmp/reports           # 只生成Excel报告到指定目录"
    echo "  ./dbcli.sh --parallel 8 --timeout 60         # 设置8个并发线程，60秒超时"
    echo "  ./dbcli.sh --encrypt                          # 加密配置文件"
    echo "  ./dbcli.sh --log-level DEBUG                  # 启用调试日志"
    echo ""
    echo "配置文件:"
    echo "  configs/     - 数据库连接配置文件目录"
    echo "  metrics/     - 监控指标配置文件目录"
    echo "  reports/     - 生成的报告文件目录"
    echo "  logs/        - 日志文件目录"
    echo "  lib/         - 数据库驱动JAR文件目录"
    echo ""
    echo "更多信息请查看 README.md 和 examples/ 目录"
}

# 设置应用程序路径
APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$APP_HOME/target/dbcli-1.0.0.jar"
LIB_DIR="$APP_HOME/lib"

# 检查是否显示帮助（任意位置参数均可触发，且不启动 Java）
for arg in "$@"; do
    if [[ "$arg" == "-h" || "$arg" == "--help" ]]; then
        show_help
        exit 0
    fi
done

print_info "启动多数据库指标收集工具 dbcli v1.0.0"
print_info "应用目录: $APP_HOME"

# 检查Java环境
print_info "检查Java环境..."
if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
    print_success "使用JAVA_HOME: $JAVA_HOME"
else
    JAVA_CMD="java"
    print_warning "未设置JAVA_HOME，使用系统默认Java"
fi

if ! command -v $JAVA_CMD &> /dev/null; then
    print_error "找不到Java运行环境"
    echo "请安装Java 8或更高版本，或设置JAVA_HOME环境变量"
    echo "下载地址: https://adoptopenjdk.net/"
    exit 1
fi

# 获取Java版本
JAVA_VERSION=$($JAVA_CMD -version 2>&1 | head -n 1 | cut -d'"' -f2)
print_success "Java版本: $JAVA_VERSION"

# 检查JAR文件是否存在
print_info "检查应用程序文件..."
# 优先查找发布根目录下的 JAR（适配发布包）
if [ -f "$APP_HOME/dbcli-1.0.0.jar" ]; then
    JAR_FILE="$APP_HOME/dbcli-1.0.0.jar"
else
    # 兼容多种命名的可执行 JAR
    CAND=$(ls "$APP_HOME"/dbcli-*.jar 2>/dev/null | head -n 1)
    if [ -n "$CAND" ]; then
        JAR_FILE="$CAND"
    elif [ -f "$APP_HOME/target/dbcli-1.0.0.jar" ]; then
        JAR_FILE="$APP_HOME/target/dbcli-1.0.0.jar"
    else
        CAND=$(ls "$APP_HOME"/target/dbcli-*.jar 2>/dev/null | head -n 1)
        if [ -n "$CAND" ]; then
            JAR_FILE="$CAND"
        fi
    fi
fi

if [ ! -f "$JAR_FILE" ]; then
    print_error "找不到JAR文件"
    echo ""
    echo "请先构建项目:"
    echo "  ./build.sh"
    echo "或者:"
    echo "  mvn clean package -DskipTests"
    exit 1
fi

JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
print_success "找到JAR文件: $JAR_FILE ($JAR_SIZE)"

# 构建类路径
print_info "构建类路径..."
CLASSPATH="$JAR_FILE"
DRIVER_COUNT=0

if [ -d "$LIB_DIR" ]; then
    for jar in "$LIB_DIR"/*.jar; do
        if [ -f "$jar" ]; then
            CLASSPATH="$CLASSPATH:$jar"
            DRIVER_COUNT=$((DRIVER_COUNT + 1))
        fi
    done
fi

if [ $DRIVER_COUNT -gt 0 ]; then
    print_success "加载了 $DRIVER_COUNT 个数据库驱动"
else
    print_warning "未找到数据库驱动文件"
    echo "请将数据库驱动JAR文件放入 lib/ 目录:"
    echo "  - Oracle: ojdbc8.jar"
    echo "  - MySQL: mysql-connector-java.jar"
    echo "  - PostgreSQL: postgresql.jar"
    echo "  - 达梦: DmJdbcDriver18.jar"
fi

# 设置JVM参数
JVM_OPTS="-Xms512m -Xmx2g"
JVM_OPTS="$JVM_OPTS -Dfile.encoding=UTF-8"
JVM_OPTS="$JVM_OPTS -Duser.timezone=Asia/Shanghai"
JVM_OPTS="$JVM_OPTS -Djava.awt.headless=true"
JVM_OPTS="$JVM_OPTS -XX:+UseG1GC"
JVM_OPTS="$JVM_OPTS -XX:+UseStringDeduplication"

# 创建必要的目录
print_info "创建必要的目录..."
mkdir -p "$APP_HOME/configs"
mkdir -p "$APP_HOME/metrics"
mkdir -p "$APP_HOME/reports"
mkdir -p "$APP_HOME/logs"
mkdir -p "$APP_HOME/lib"
print_success "目录创建完成"

# 检查配置文件
print_info "检查配置文件..."
CONFIG_COUNT=$(find "$APP_HOME/configs" -name "*.yml" -o -name "*.yaml" 2>/dev/null | wc -l)
METRIC_COUNT=$(find "$APP_HOME/metrics" -name "*.yml" -o -name "*.yaml" 2>/dev/null | wc -l)

if [ $CONFIG_COUNT -eq 0 ]; then
    print_warning "未找到数据库配置文件"
    echo "请在 configs/ 目录中添加数据库配置文件，或运行:"
    echo "  ./dbcli.sh --template"
else
    print_success "找到 $CONFIG_COUNT 个数据库配置文件"
fi

if [ $METRIC_COUNT -eq 0 ]; then
    print_warning "未找到指标配置文件"
    echo "请在 metrics/ 目录中添加指标配置文件"
else
    print_success "找到 $METRIC_COUNT 个指标配置文件"
fi

# 运行应用程序
echo ""
print_info "启动应用程序..."
echo "执行命令: $JAVA_CMD $JVM_OPTS -cp \"$CLASSPATH\" com.dbcli.DbCliApplication $*"
echo ""

# 记录启动时间
START_TIME=$(date +%s)

# 执行应用程序
$JAVA_CMD $JVM_OPTS -cp "$CLASSPATH" com.dbcli.DbCliApplication "$@"

# 检查退出码
EXIT_CODE=$?
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    print_success "程序执行成功！耗时: ${DURATION}秒"
    
    # 显示生成的报告文件
    if [ -d "$APP_HOME/reports" ]; then
        REPORT_COUNT=$(find "$APP_HOME/reports" -name "*.xlsx" -o -name "*.html" 2>/dev/null | wc -l)
        if [ $REPORT_COUNT -gt 0 ]; then
            print_success "生成了 $REPORT_COUNT 个报告文件:"
            find "$APP_HOME/reports" -name "*.xlsx" -o -name "*.html" 2>/dev/null | head -5 | while read file; do
                echo "  📄 $file"
            done
            if [ $REPORT_COUNT -gt 5 ]; then
                echo "  ... 还有 $((REPORT_COUNT - 5)) 个文件"
            fi
        fi
    fi
else
    print_error "程序执行失败！退出码: $EXIT_CODE，耗时: ${DURATION}秒"
    echo ""
    echo "🔧 故障排除建议:"
    echo "1. 检查日志文件: logs/dbcli_INFO.log 或 logs/dbcli_ERROR.log"
    echo "2. 验证数据库连接: ./dbcli.sh --test"
    echo "3. 检查配置文件格式: examples/sample-configs/"
    echo "4. 查看详细帮助: ./dbcli.sh --help"
    exit $EXIT_CODE
fi
