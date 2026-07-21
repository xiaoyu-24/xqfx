# 备份与恢复检查表

## 备份批次

一次备份必须同时包含：

1. MySQL 导出文件 `requirements.sql`。
2. 完整附件目录 `attachments`。
3. `manifest.json`，记录同一批次号和创建时间。

预览目录属于可重建缓存，默认不要求备份。若选择备份预览目录，必须与数据库和原始附件使用同一批次，不能单独恢复旧预览缓存。

使用 [deployment.md](deployment.md) 的命令每天执行一次，至少保留最近 7 个批次。不要只备份数据库：附件实体文件不在 MySQL 中。

## 恢复步骤

1. 停止后端写入，并确认待恢复批次的 SQL、附件目录和 manifest 都存在。
2. 新建或清空目标数据库；导入 SQL：`mysql -u requirements_app -p requirements_platform < requirements.sql`。
3. 将同批次 `attachments` 复制为 `ATTACHMENTS_ROOT` 指向的目录。
4. 配置数据库连接、原始附件目录、预览目录和LibreOffice可执行路径，启动后端。
5. 检查 `/api/health` 返回 `{"status":"UP"}`，并抽查系统、需求、附件下载、图片和PDF预览。
6. 对一个Word和一个Excel附件点击“重新生成”，确认恢复环境能够重新生成PDF预览。

## 已完成的演练记录

2026-07-10：使用临时 MySQL 8.4 空库启动应用，Flyway 成功应用 V1–V4；检查确认 `attachments.checksum` 和 `requirements.record_version` 已创建，应用接口 `/api/systems` 返回 200。随后创建一条带 PDF 附件的需求，以最小权限账号使用 `--no-tablespaces` 导出数据库并复制附件目录，恢复到独立数据库和目录。恢复后的应用健康检查返回 UP，需求详情和附件下载均成功。演练使用独立临时容器和临时附件目录，已清理，不影响本地业务数据库。
