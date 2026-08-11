# MySQL 5.7 迁移恢复设计

## 背景与目标

服务器数据库已恢复到 Flyway V10，本地数据库已执行到 V22。服务器使用 InnoDB 时，V12 和 V15 已经创建外键，V13 和 V16 再次添加同名外键会在 MySQL 5.7.26 报错 1022。

本次修复必须同时满足：

- 服务器能够从 V10 连续迁移到最新版本。
- 不修改已经执行过的 V13、V16 和 V22，保持本地数据库的 Flyway checksum 有效。
- 迁移中途失败后可以安全重试。
- 新增管理员账号 `admin`，姓名为“管理员”，角色为 `ADMIN`，初始密码为系统统一密码 `888888`，首次登录必须修改密码。
- 如果 `admin` 已存在，不修改其姓名、角色、密码或状态。

## 迁移修复

在现有 `db/migration` 位置新增 Flyway `beforeEachMigrate` SQL 回调，不新增历史版本，也不改变现有迁移 checksum。

回调在每个待执行版本前读取 `flyway_schema_history` 的当前成功版本：

1. 当前版本为 V12 时，检查 `requirements` 上的 `fk_requirements_department` 和 `fk_requirements_type`。存在则删除，不存在则不操作。随后原 V13 负责统一转换为 InnoDB 并重新建立外键。
2. 当前版本为 V15 时，检查 `notifications` 上的 `fk_notifications_recipient` 和 `fk_notifications_requirement`。存在则删除，不存在则不操作。随后原 V16 负责统一转换为 InnoDB 并重新建立外键。
3. 其他版本不执行结构变更。

MySQL 5.7 不支持 `DROP FOREIGN KEY IF EXISTS`，回调通过 `information_schema` 判断并使用 `PREPARE` 执行条件 DDL。若回调执行后、版本迁移前进程中断，下一次执行会发现外键已经不存在并继续运行，因此具备可重入性。

## 管理员迁移

新增 V23 迁移，不修改 V22。V23 使用 `INSERT ... SELECT ... WHERE NOT EXISTS` 按用户名幂等创建管理员：

- `username`: `admin`
- `display_name`: `管理员`
- `role`: `ADMIN`
- `password_hash`: `888888` 的 BCrypt 哈希
- `must_change_password`: `TRUE`
- `disabled`: `FALSE`
- `department_id`: `NULL`

只要已有任意大小写规则下匹配唯一索引的 `admin` 账号，V23 就跳过写入，保留原账号全部数据。

## 验证方案

测试使用隔离的 MySQL 5.7.26 实例，并遵循测试先行：

1. 在没有回调的代码上运行 V10 到最新版本，确认测试因 V13 的错误 1022 失败。
2. 加入回调后重新运行，确认 V10 到 V23 一次完成，V13、V16 均为成功状态。
3. 检查四个目标外键最终各存在一份。
4. 无 `admin` 账号场景确认创建 `admin`，角色为 `ADMIN`，密码匹配 `888888`，并要求首次改密。
5. 预置 `admin` 场景确认 V23 不修改已有账号。
6. 克隆本地 V22 结构运行迁移，确认旧 checksum 校验通过且 V23 幂等完成。

临时测试文件、数据库和日志在验证结束后删除，不修改服务器数据库。

## 部署约束

服务器部署前仍需备份。数据库必须确实处于 V10 且没有 `success=0` 的失败记录；如果存在失败记录，应先核对半完成结构再处理，不能直接反复重启。部署新 JAR 后由 Flyway 一次执行 V11 到 V23，成功后检查版本历史、管理员账号和四个目标外键。
