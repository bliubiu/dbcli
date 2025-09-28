# 多阶段构建 Dockerfile
# 第一阶段：构建阶段
FROM maven:3.8-openjdk-8 AS builder

LABEL stage=builder
WORKDIR /build

# 复制 pom.xml 并下载依赖（利用Docker缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码并构建
COPY src ./src
RUN mvn clean package -DskipTests -B

# 第二阶段：运行时镜像
FROM openjdk:8-jre-alpine

LABEL maintainer="dbcli-team" \
      version="1.0.0" \
      description="DBCLI - 多数据库指标收集与报告工具"

# 安装必要工具和设置时区
RUN apk add --no-cache \
    curl \
    bash \
    tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && apk del tzdata

# 创建应用用户（安全最佳实践）
RUN addgroup -g 1000 dbcli && \
    adduser -D -s /bin/bash -u 1000 -G dbcli dbcli

# 创建应用目录结构
WORKDIR /app
RUN mkdir -p configs metrics lib logs reports temp && \
    chown -R dbcli:dbcli /app

# 切换到应用用户
USER dbcli

# 从构建阶段复制JAR文件
COPY --from=builder --chown=dbcli:dbcli /build/target/dbcli-1.0.0.jar app.jar

# 复制配置文件和指标定义
COPY --chown=dbcli:dbcli configs/ configs/
COPY --chown=dbcli:dbcli metrics/ metrics/

# 复制数据库驱动（如果存在）
COPY --chown=dbcli:dbcli lib/ lib/

# 复制启动脚本
COPY --chown=dbcli:dbcli docker-entrypoint.sh /app/
RUN chmod +x docker-entrypoint.sh

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/status || exit 1

# 设置环境变量
ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC -XX:+UseStringDeduplication" \
    DBCLI_LOG_LEVEL="INFO" \
    DBCLI_WEB_PORT="8080"

# 数据卷
VOLUME ["/app/configs", "/app/metrics", "/app/lib", "/app/logs", "/app/reports"]

# 启动应用
ENTRYPOINT ["./docker-entrypoint.sh"]
CMD ["-c", "configs", "-m", "metrics", "-o", "reports", "-f", "excel"]