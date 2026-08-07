# MySQL 5.7.32 与 Java 17 兼容性改造设计

## 背景

项目当前使用 Spring Boot 3.4.5、Hibernate 6.6.13.Final、Java 21，数据库目标环境固定为 MySQL 5.7.32。Hibernate 6.6 已明确提示 MySQL 5.7 不在支持范围内；现有 Testcontainers 集成测试使用 MySQL 8.4，无法作为目标环境验证。

## 目标

在不升级 MySQL 服务器、不修改已执行 Flyway 历史迁移的前提下：

1. 将后端构建和运行基线调整为 Java 17。
2. 使用对 MySQL 5.7 兼容的 Spring Boot/Hibernate 和 Connector/J 组合。
3. 让集成测试真实覆盖 MySQL 5.7.32。
4. 补齐 CentOS 7/宝塔部署所需的环境变量、Nginx 和进程托管模板。
5. 保留现有业务行为和已修复的问题回归测试。

## 方案选择

采用 Spring Boot 3.3.x + Hibernate 6.5.x + MySQL Connector/J 8.0.33 + Java 17。

- 继续使用 Jakarta API，避免退回 Spring Boot 2.x 引起的 javax/jakarta 大范围迁移。
- 不使用自定义 Hibernate 方言强行规避版本检查。
- Connector/J 显式固定为 8.0.33，避免随 Spring Boot 依赖管理升级到不适合 MySQL 5.7 的 9.x。
- 如果候选 Spring Boot 3.3.x 版本在真实 MySQL 5.7.32 集成测试中仍不兼容，再评估 Spring Boot 3.2.x/Hibernate 6.4.x；本轮不预先扩大降级范围。

## 修改范围

### 后端依赖与配置

- `backend/pom.xml`：Spring Boot 3.3.x、编译 release 17、Connector/J 8.0.33。
- `backend/src/main/resources/application.yml`：保留环境变量注入，增加稳定连接池和时区配置；不写入密码。
- 不修改业务表的历史 Flyway 脚本；如发现 5.7 专属结构差异，新增版本脚本。

### 测试

- 新增依赖兼容性回归测试，防止 Hibernate 6.6/Connector 9.x 被重新引入。
- `MySqlMigrationIntegrationTest` 使用 `mysql:5.7.32`，并断言实际数据库版本。
- 保留全量 API、服务、前端测试，增加 MySQL 5.7.32 上的事务、字符集、附件和迁移验证。

### 部署结构

- 新增 `deploy/centos7/` 配置模板：环境变量、Java 服务、Nginx 代理、健康检查和备份脚本。
- 生产附件目录必须通过 `ATTACHMENTS_ROOT` 指向绝对路径。
- `/api/health` 作为健康检查地址，Nginx 将 `/api` 代理到后端 8080。

## 数据流与运行方式

浏览器请求同源 Nginx；静态资源由 Nginx 提供，`/api` 请求转发到 Java 进程。Java 进程从环境变量读取数据库和附件配置，启动时由 Flyway 校验并执行迁移，业务数据写入 MySQL 5.7.32，附件写入独立目录。

## 测试与验收

- Java 17 编译成功。
- MySQL 5.7.32 Testcontainers 测试实际执行且通过，不允许因 Docker 缺失而将其视为通过。
- 后端全量测试 0 失败、0 错误。
- 前端测试和生产构建通过。
- Flyway V1–V7 成功，健康检查返回 UP。
- 系统、需求、版本、状态、迁移、附件和异常接口通过。
- 超长协助人、非法枚举、未知路径和失败事务行为保持正确。

## 回滚

- 依赖回滚到当前已验证构建的 jar。
- 配置文件通过版本化模板回滚。
- 任何新增数据库迁移前先完成数据库和附件双备份；Flyway 不依赖自动回滚。

## 非目标

- 本轮不升级 MySQL 服务器。
- 本轮不进行 CentOS 服务器实际部署。
- 本轮不重构无关业务模块、不改变前端业务流程。
