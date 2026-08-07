# 更新代码并上传服务器

这份文档用于项目每次更新时，将本地构建产物上传到宝塔服务器。

文档文件属于本地部署说明，项目 `.gitignore` 已忽略所有 `*.md` 文件，不会提交到 GitHub。

## 一、本地构建

### 1. 构建后端

在项目根目录执行：

```powershell
cd D:\projects\xqfx\backend
mvn clean package
```

确认输出：

```text
BUILD SUCCESS
```

生成文件：

```text
D:\projects\xqfx\backend\target\requirements-platform-0.1.0-SNAPSHOT.jar
```

### 2. 测试并构建前端

```powershell
cd D:\projects\xqfx\frontend

npm ci
npm test
npm run build
```

确认生成目录：

```text
D:\projects\xqfx\frontend\dist
```

### 3. 打包前端

```powershell
cd D:\projects\xqfx\frontend

Remove-Item .\frontend-dist.zip -Force -ErrorAction SilentlyContinue

Compress-Archive `
  -Path .\dist\* `
  -DestinationPath .\frontend-dist.zip `
  -CompressionLevel Optimal
```

最终需要上传两个文件：

```text
D:\projects\xqfx\backend\target\requirements-platform-0.1.0-SNAPSHOT.jar
D:\projects\xqfx\frontend\frontend-dist.zip
```

## 二、上传两个文件

上传到服务器目录：

```text
/www/wwwroot/requirements-platform/update/
```

文件名必须保持为：

```text
requirements-platform-0.1.0-SNAPSHOT.jar
frontend-dist.zip
```

不要直接上传到正在运行的 `backend` 或 `frontend/dist` 目录。

## 三、执行一键更新

服务器执行：

```bash
bash /www/wwwroot/requirements-platform/deploy.sh
```

看到以下提示，说明 JAR 和前端已经替换完成：

```text
文件更新完成。
请进入宝塔重启 Java 项目 requirements-api。
```

脚本会负责备份当前 JAR 和前端目录，并将新文件切换到生产目录。

## 四、重启 Java 项目

进入宝塔面板：

```text
Java项目 → requirements-api → 重启
```

前端目录路径没有改变，不需要重启 Nginx。

## 五、验证更新结果

### 1. 在服务器验证后端

```bash
curl --max-time 15 http://127.0.0.1:8080/api/health
```

预期返回：

```json
{"status":"UP"}
```

### 2. 在本地验证公网接口

```powershell
curl.exe https://requirement.wltlink.com/api/health
```

### 3. 浏览器刷新

```text
Ctrl + F5
```

然后检查需求列表、管理需求、需求填写和附件功能是否正常。

本次附件预览升级还需要检查：

1. `.env` 已配置 `ATTACHMENTS_PREVIEW_ROOT`、`LIBREOFFICE_EXECUTABLE`、`ATTACHMENTS_PREVIEW_TIMEOUT_SECONDS` 和 `ATTACHMENTS_PREVIEW_CONCURRENCY`。
2. 服务器已安装LibreOffice，并可由 `requirements` 账号执行无界面转换。
3. 原始附件目录和预览目录对服务账号可写。
4. 图片和PDF可直接预览，Word和Excel可转换后预览，原文件下载保持正常。

## 六、完整更新顺序

```text
本地打包
→ 上传 JAR 和 frontend-dist.zip 到 update/
→ 执行 deploy.sh
→ 宝塔重启 Java 项目 requirements-api
→ 验证 /api/health
→ 浏览器 Ctrl + F5
```

## 七、数据库迁移提醒

如果本次更新包含新的 Flyway 数据库迁移脚本，执行 `deploy.sh` 前额外备份数据库：

```bash
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_DIR=/www/backup/requirements-platform/$STAMP

mkdir -p "$BACKUP_DIR"

mysqldump \
  --single-transaction \
  --routines \
  --events \
  --no-tablespaces \
  -ureq_app \
  -p \
  requirements_platform \
  > "$BACKUP_DIR/requirements_platform.sql"
```

普通前端调整和后端业务代码更新，可以直接使用本流程。

本次版本包含V8迁移，必须先完成数据库和原始附件同批次备份，再重启后端执行迁移。
