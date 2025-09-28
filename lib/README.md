# 外部数据库驱动管理

本目录用于存放外部数据库驱动JAR文件，这些驱动不包含在Maven依赖中，需要手动下载并放置在此目录。

## 支持的数据库驱动

### 1. Oracle数据库
- **驱动文件**: `ojdbc8.jar` 或 `ojdbc11.jar`
- **下载地址**: https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html
- **版本要求**: 12.2.0.1 或更高版本
- **驱动类**: `oracle.jdbc.OracleDriver`

### 2. MySQL数据库
- **驱动文件**: `mysql-connector-java-8.0.33.jar`
- **Maven依赖**: 已包含在pom.xml中
- **驱动类**: `com.mysql.cj.jdbc.Driver`

### 3. PostgreSQL数据库
- **驱动文件**: `postgresql-42.6.0.jar`
- **Maven依赖**: 已包含在pom.xml中
- **驱动类**: `org.postgresql.Driver`

### 4. 达梦数据库
- **驱动文件**: `DmJdbcDriver18.jar`
- **下载地址**: 从达梦官网下载或联系达梦技术支持
- **版本要求**: DM8 或更高版本
- **驱动类**: `dm.jdbc.driver.DmDriver`

## 驱动安装说明

### 方法一：手动复制（推荐）
1. 下载对应的数据库驱动JAR文件
2. 将JAR文件复制到 `lib/` 目录下
3. 重新构建项目：`mvn clean package`

### 方法二：Maven本地安装
对于无法通过Maven中央仓库获取的驱动（如Oracle、达梦），可以安装到本地Maven仓库：

```bash
# Oracle驱动安装示例
mvn install:install-file -Dfile=ojdbc8.jar -DgroupId=com.oracle.database.jdbc -DartifactId=ojdbc8 -Dversion=21.1.0.0 -Dpackaging=jar

# 达梦驱动安装示例
mvn install:install-file -Dfile=DmJdbcDriver18.jar -DgroupId=com.dameng -DartifactId=dm-jdbc -Dversion=8.1.1.49 -Dpackaging=jar
```

然后在pom.xml中添加对应依赖。

## 驱动加载机制

应用程序启动时会自动扫描以下位置的驱动：
1. Maven依赖中的驱动（MySQL、PostgreSQL）
2. `lib/` 目录下的JAR文件（Oracle、达梦等）
3. 系统CLASSPATH中的驱动

## 连接字符串格式

### Oracle
```
jdbc:oracle:thin:@host:port:service_name
jdbc:oracle:thin:@host:port/service_name
```

### MySQL
```
jdbc:mysql://host:port/database?useSSL=false&serverTimezone=UTC
```

### PostgreSQL
```
jdbc:postgresql://host:port/database
```

### 达梦
```
jdbc:dm://host:port
jdbc:dm://host:port/schema
```

## 注意事项

1. **版本兼容性**: 确保驱动版本与数据库服务器版本兼容
2. **许可证**: 注意各数据库驱动的许可证要求
3. **安全性**: 生产环境中建议使用最新稳定版本的驱动
4. **性能**: 不同版本的驱动可能在性能上有差异，建议进行测试

## 故障排除

### 常见问题

1. **ClassNotFoundException**: 驱动JAR文件未正确加载
   - 检查JAR文件是否在lib目录中
   - 确认JAR文件完整且未损坏

2. **SQLException: No suitable driver**: 驱动类未注册
   - 检查驱动类名是否正确
   - 确认JDBC URL格式正确

3. **连接超时**: 网络或防火墙问题
   - 检查数据库服务器是否可达
   - 确认端口是否开放

### 调试方法

启用JDBC调试日志：
```bash
java -Djava.util.logging.config.file=logging.properties -jar dbcli.jar
```

在logging.properties中添加：
```properties
java.sql.level=FINE
oracle.jdbc.level=FINE