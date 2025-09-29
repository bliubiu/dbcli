#!/bin/bash

# 简化版 dbcli 运行脚本
# 支持命令行模式运行和启动Web管理界面

echo "=========================================="
echo "dbcli 简化运行脚本"
echo "=========================================="

# 检查参数
for arg in "$@"; do
    if [[ "$arg" == "-h" || "$arg" == "--help" ]]; then
        echo ""
        echo "用法: ./run.sh [选项]"
        echo ""
        echo "选项:"
        echo "  -w, --web, --web-management  启动Web管理界面"
        echo "  -h, --help                   显示此帮助信息"
        echo ""
        echo "示例:"
        echo "  ./run.sh                      # 编译并运行命令行模式"
        echo "  ./run.sh -w                   # 启动Web管理界面"
        echo ""
        exit 0
    fi
done

# 检查是否为Web管理模式
WEB_MODE=false
for arg in "$@"; do
    if [[ "$arg" == "--web" || "$arg" == "--web-management" || "$arg" == "-w" ]]; then
        WEB_MODE=true
        break
    fi
done

if [ "$WEB_MODE" = true ]; then
    echo "启动Web管理界面..."
    ./dbcli.sh "$@"
else
    echo "编译并运行..."
    # 简化执行流程
    mvn -q -DskipTests clean compile
    if [ $? -ne 0 ]; then
        echo "编译失败"
        exit 1
    fi
    
    mvn package -DskipTests -q
    if [ $? -ne 0 ]; then
        echo "打包失败"
        exit 1
    fi
    
    ./dbcli.sh "$@"
fi