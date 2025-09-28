#!/bin/bash

# 多数据库指标收集工具 dbcli 发布包脚本
# 版本: 1.0.0
# 作者: dbcli开发团队

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

echo "=========================================="
echo "多数据库指标收集工具 dbcli v1.0.0"
echo "发布包脚本"
echo "=========================================="

# 获取脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

VERSION="1.0.0"
PACKAGE_NAME="dbcli-${VERSION}"
PACKAGE_DIR="dist/${PACKAGE_NAME}"
ARCHIVE_NAME="${PACKAGE_NAME}.tar.gz"

print_info "项目目录: $SCRIPT_DIR"
print_info "发布版本: $VERSION"
print_info "打包时间: $(date)"
echo ""

# 检查是否已构建
JAR_FILE="target/dbcli-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    print_error "未找到构建的JAR文件: $JAR_FILE"
    echo "请先运行构建脚本:"
    echo "  ./build.sh"
    exit 1
fi

print_success "找到构建的JAR文件: $JAR_FILE"

# 清理旧的发布包
print_info "清理旧的发布包..."
if [ -d "dist" ]; then
    rm -rf dist
fi
mkdir -p "$PACKAGE_DIR"
print_success "创建发布目录: $PACKAGE_DIR"

# 复制核心文件
print_info "复制核心文件..."
cp "$JAR_FILE" "$PACKAGE_DIR/"
cp "dbcli.sh" "$PACKAGE_DIR/"
cp "dbcli.bat" "$PACKAGE_DIR/"
cp "build.sh" "$PACKAGE_DIR/"
cp "build.bat" "$PACKAGE_DIR/"
chmod +x "$PACKAGE_DIR/dbcli.sh"
chmod +x "$PACKAGE_DIR/build.sh"
print_success "复制启动脚本和构建脚本"

# 复制配置文件
print_info "复制配置文件..."
cp -r "configs" "$PACKAGE_DIR/"
cp -r "metrics" "$PACKAGE_DIR/"
print_success "复制配置文件目录"

# 复制示例文件
print_info "复制示例文件..."
cp -r "examples" "$PACKAGE_DIR/"
print_success "复制示例文件目录"

# 复制文档文件
print_info "复制文档文件..."
cp "README.md" "$PACKAGE_DIR/"
if [ -f "Java数据库命令行工具dbcli.md" ]; then
    cp "Java数据库命令行工具dbcli.md" "$PACKAGE_DIR/"
fi
if [ -d "docs" ]; then
    cp -r "docs" "$PACKAGE_DIR/"
fi
print_success "复制文档文件"

# 创建必要的目录
print_info "创建必要的目录..."
mkdir -p "$PACKAGE_DIR/lib"
mkdir -p "$PACKAGE_DIR/reports"
mkdir -p "$PACKAGE_DIR/log"

# 创建lib目录说明文件
cat > "$PACKAGE_DIR/lib/README.md" << 'EOF'
# 数据库驱动文件目录

请将相应的数据库驱动JAR文件放入此目录：

## Oracle数据库
- 文件名: ojdbc8.jar 或 ojdbc11.jar
- 下载地址: https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html

## MySQL数据库
- 文件名: mysql-connector-java-8.0.33.jar
- 下载地址: https://dev.mysql.com/downloads/connector/j/

## PostgreSQL数据库
- 文件名: postgresql-42.6.0.jar
- 下载地址: https://jdbc.postgresql.org/download.html

## 达梦数据库
- 文件名: DmJdbcDriver18.jar
- 下载地址: https://www.dameng.com/list_103.html

## 注意事项
1. 请确保驱动版本与数据库版本兼容
2. 驱动文件名可以不同，但必须是有效的JDBC驱动JAR文件
3. 启动时会自动加载此目录下的所有JAR文件
EOF

print_success "创建目录结构"

# 创建快速开始脚本
print_info "创建快速开始脚本..."
cat > "$PACKAGE_DIR/quick-start.sh" << 'EOF'
#!/bin/bash

# dbcli 快速开始脚本

echo "=========================================="
echo "dbcli 快速开始向导"
echo "=========================================="

echo "1. 生成配置模板..."
./dbcli.sh --template

echo ""
echo "2. 请按照以下步骤完成配置："
echo "   - 编辑 configs/ 目录下的数据库配置文件"
echo "   - 将数据库驱动JAR文件放入 lib/ 目录"
echo "   - 根据需要修改 metrics/ 目录下的指标配置"

echo ""
echo "3. 测试数据库连接："
echo "   ./dbcli.sh --test"

echo ""
echo "4. 执行指标收集："
echo "   ./dbcli.sh"

echo ""
echo "更多帮助信息："
echo "   ./dbcli.sh --help"
EOF

cat > "$PACKAGE_DIR/quick-start.bat" << 'EOF'
@echo off

REM dbcli 快速开始脚本

echo ==========================================
echo dbcli 快速开始向导
echo ==========================================

echo 1. 生成配置模板...
dbcli.bat --template

echo.
echo 2. 请按照以下步骤完成配置：
echo    - 编辑 configs\ 目录下的数据库配置文件
echo    - 将数据库驱动JAR文件放入 lib\ 目录
echo    - 根据需要修改 metrics\ 目录下的指标配置

echo.
echo 3. 测试数据库连接：
echo    dbcli.bat --test

echo.
echo 4. 执行指标收集：
echo    dbcli.bat

echo.
echo 更多帮助信息：
echo    dbcli.bat --help

pause
EOF

chmod +x "$PACKAGE_DIR/quick-start.sh"
print_success "创建快速开始脚本"

# 创建安装说明文件
print_info "创建安装说明文件..."
cat > "$PACKAGE_DIR/INSTALL.md" << 'EOF'
# dbcli 安装和部署指南

## 系统要求

- Java 8 或更高版本
- 支持的操作系统：Linux、macOS、Windows
- 内存：建议 2GB 以上
- 磁盘空间：100MB 以上

## 安装步骤

### 1. 解压发布包
```bash
tar -xzf dbcli-1.0.0.tar.gz
cd dbcli-1.0.0
```

### 2. 安装数据库驱动
将相应的数据库驱动JAR文件放入 `lib/` 目录：
- Oracle: ojdbc8.jar
- MySQL: mysql-connector-java.jar  
- PostgreSQL: postgresql.jar
- 达梦: DmJdbcDriver18.jar

### 3. 配置数据库连接
编辑 `configs/` 目录下的配置文件，或使用模板生成：
```bash
# Linux/macOS
./dbcli.sh --template

# Windows
dbcli.bat --template
```

### 4. 配置监控指标
根据需要修改 `metrics/` 目录下的指标配置文件。

### 5. 测试连接
```bash
# Linux/macOS
./dbcli.sh --test

# Windows
dbcli.bat --test
```

### 6. 运行监控
```bash
# Linux/macOS
./dbcli.sh

# Windows
dbcli.bat
```

## 目录结构

```
dbcli-1.0.0/
├── dbcli-1.0.0.jar     # 主程序JAR文件
├── dbcli.sh            # Linux/macOS启动脚本
├── dbcli.bat           # Windows启动脚本
├── build.sh            # Linux/macOS构建脚本
├── build.bat           # Windows构建脚本
├── quick-start.sh      # 快速开始脚本(Linux/macOS)
├── quick-start.bat     # 快速开始脚本(Windows)
├── README.md           # 项目说明文档
├── INSTALL.md          # 安装部署指南
├── configs/            # 数据库配置文件目录
├── metrics/            # 监控指标配置目录
├── examples/           # 示例文件目录
├── lib/                # 数据库驱动目录
├── reports/            # 报告输出目录
└── log/                # 日志文件目录
```

## 常见问题

### Q: 提示找不到Java环境
A: 请安装Java 8或更高版本，并设置JAVA_HOME环境变量。

### Q: 数据库连接失败
A: 请检查：
1. 数据库驱动是否正确放入lib目录
2. 数据库连接配置是否正确
3. 网络连接是否正常
4. 数据库用户权限是否足够

### Q: 生成的报告为空
A: 请检查：
1. 指标配置文件是否正确
2. SQL语句是否能正常执行
3. 数据库用户是否有查询权限

更多问题请查看项目文档或提交Issue。
EOF

print_success "创建安装说明文件"

# 计算包大小
print_info "计算发布包大小..."
PACKAGE_SIZE=$(du -sh "$PACKAGE_DIR" | cut -f1)
print_success "发布包大小: $PACKAGE_SIZE"

# 创建压缩包
print_info "创建压缩包..."
cd dist
tar -czf "$ARCHIVE_NAME" "$PACKAGE_NAME"
cd ..

if [ -f "dist/$ARCHIVE_NAME" ]; then
    ARCHIVE_SIZE=$(du -sh "dist/$ARCHIVE_NAME" | cut -f1)
    print_success "创建压缩包: dist/$ARCHIVE_NAME ($ARCHIVE_SIZE)"
else
    print_error "创建压缩包失败"
    exit 1
fi

# 生成校验和
print_info "生成校验和..."
cd dist
sha256sum "$ARCHIVE_NAME" > "${ARCHIVE_NAME}.sha256"
md5sum "$ARCHIVE_NAME" > "${ARCHIVE_NAME}.md5"
cd ..

print_success "生成校验和文件"

echo ""
echo "=========================================="
echo "发布包创建完成！"
echo "=========================================="
echo ""
echo "📦 发布包目录: $PACKAGE_DIR"
echo "📦 压缩包文件: dist/$ARCHIVE_NAME ($ARCHIVE_SIZE)"
echo "🔐 校验和文件: dist/${ARCHIVE_NAME}.sha256, dist/${ARCHIVE_NAME}.md5"
echo ""
echo "📋 发布清单:"
echo "✅ 核心程序文件 (JAR + 启动脚本)"
echo "✅ 配置文件和示例"
echo "✅ 文档和说明"
echo "✅ 目录结构"
echo "✅ 快速开始脚本"
echo ""
echo "🚀 部署方法:"
echo "1. 将压缩包传输到目标服务器"
echo "2. 解压: tar -xzf $ARCHIVE_NAME"
echo "3. 进入目录: cd $PACKAGE_NAME"
echo "4. 运行快速开始: ./quick-start.sh"
echo ""
echo "📚 更多信息请查看 INSTALL.md 文件"

print_success "发布包创建完成！"