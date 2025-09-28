# Oracle JDBC 驱动安装说明

## 问题描述
当前项目缺少Oracle JDBC驱动，导致无法连接Oracle数据库。

## 解决方案

### 方法1：从Oracle官网下载
1. 访问Oracle官网：https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html
2. 下载适合的版本（推荐 ojdbc8.jar 或 ojdbc11.jar）
3. 将下载的jar文件放入 `lib/` 目录

### 方法2：使用Maven下载
```bash
mvn dependency:get -Dartifact=com.oracle.database.jdbc:ojdbc8:21.7.0.0 -Ddest=lib/ojdbc8.jar
```

### 方法3：手动添加到Maven依赖
在pom.xml中添加：
```xml
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc8</artifactId>
    <version>21.7.0.0</version>
</dependency>
```

## 验证安装
添加驱动后，重新运行测试：
```bash
java -cp "target\classes;lib\*" com.dbcli.DbCliApplication --test
```

## 注意事项
- Oracle JDBC驱动由于许可证限制，无法直接包含在开源项目中
- 请确保使用的驱动版本与目标Oracle数据库版本兼容
- 驱动文件名建议为：ojdbc8.jar 或 ojdbc11.jar