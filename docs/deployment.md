# 本地部署与迁移说明

## 前置条件

- Java 21。
- MySQL 8.0，创建数据库 `requirements_platform` 和最小权限账号。
- Node.js 20+（仅构建前端时需要）。

## 后端配置

通过环境变量配置，不在仓库保存密码：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/requirements_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='requirements_app'
$env:DB_PASSWORD='请设置实际密码'
$env:ATTACHMENTS_ROOT='D:\requirements-platform\uploads'
java -jar backend\target\requirements-platform-0.1.0-SNAPSHOT.jar
```

Flyway 会自动执行 `V1__initial_schema.sql`。应用账号需要数据库、表和索引的创建/变更权限；日常运行时建议改用仅数据读写权限的账号。

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
mysqldump -u requirements_app -p requirements_platform | Out-File "D:\backup\requirements-$stamp.sql" -Encoding utf8
Copy-Item -Recurse $env:ATTACHMENTS_ROOT "D:\backup\attachments-$stamp"
```

至少保留最近 7 个批次。

## 恢复与迁移

1. 停止后端，避免写入。
2. 恢复同一批次的 MySQL 导出与附件目录。
3. 在新机器设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`ATTACHMENTS_ROOT`。
4. 启动后端，确认 Flyway 校验成功。
5. 访问前端，抽查系统、需求和附件下载。

