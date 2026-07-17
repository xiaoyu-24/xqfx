# 本地部署与迁移说明

## 前置条件

- Java 17。
- MySQL 5.7.32，创建数据库 `requirements_platform` 和最小权限账号。
- Node.js 20+（仅构建前端时需要）。

## 后端配置

通过环境变量配置，不在仓库保存密码：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/requirements_platform?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='requirements_app'
$env:DB_PASSWORD='请设置实际密码'
$env:ATTACHMENTS_ROOT='D:\requirements-platform\uploads'
$env:ATTACHMENTS_MINIMUM_FREE_SPACE_BYTES='1073741824' # 可选：至少保留 1 GiB 空间
Set-Location backend
mvn clean package
java -jar target\requirements-platform-0.1.0-SNAPSHOT.jar
```

CentOS 7/宝塔部署模板见 [`deploy/centos7`](../deploy/centos7)。生产环境应复制 `.env.example` 后通过宝塔进程管理或 systemd 注入环境变量，不要把实际密码提交到仓库。

前后端分域名部署时采用同源代理方式：前端继续使用 `/api`，前端域名的 Nginx 将 `/api` 代理到本机 8080；后端 API 域名可作为独立运维访问入口。这样浏览器不发生跨域请求，不需要配置 CORS 或 `VITE_API_BASE_URL`。

Flyway 会自动执行 V1–V7 迁移，包括需求处理信息字段。应用账号需要数据库、表和索引的创建/变更权限；日常运行时建议改用仅数据读写权限的账号。

启动完成后访问 `http://127.0.0.1:8080/api/health`，应返回 `{"status":"UP"}`。附件目录应由运行账号拥有读写权限；上传时会保留临时文件所需空间，并按 `ATTACHMENTS_MINIMUM_FREE_SPACE_BYTES` 预留剩余容量。

## 前端构建

```powershell
Set-Location frontend
npm ci
npm run build
```

将 `frontend/dist` 交给内网 Web 服务器托管，并将 `/api` 反向代理到后端服务。

## 备份

每天对数据库和附件目录使用同一批次标识备份：

```powershell
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$backupRoot='D:\backup'
New-Item -ItemType Directory -Force "$backupRoot\$stamp" | Out-Null
mysqldump -u requirements_app -p --single-transaction --routines --events --no-tablespaces --result-file="$backupRoot\$stamp\requirements.sql" requirements_platform
Copy-Item -Recurse $env:ATTACHMENTS_ROOT "$backupRoot\$stamp\attachments"
@{ batch=$stamp; createdAt=(Get-Date).ToString('o'); database='requirements_platform'; attachments='attachments' } | ConvertTo-Json | Set-Content "$backupRoot\$stamp\manifest.json" -Encoding utf8
```

至少保留最近 7 个批次。

## 恢复与迁移

1. 停止后端，避免写入。
2. 恢复同一批次的 MySQL 导出与附件目录：`mysql -u requirements_app -p requirements_platform < D:\backup\批次号\requirements.sql`，再将 `attachments` 目录复制到配置的附件根目录。
3. 在新机器设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`ATTACHMENTS_ROOT`。
4. 启动后端，确认 Flyway 校验成功及 `/api/health` 返回 UP。
5. 访问前端，抽查系统、需求、附件下载和图片预览。

详细备份和恢复检查表见 [backup-and-recovery.md](backup-and-recovery.md)。
