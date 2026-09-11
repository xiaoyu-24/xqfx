# 方案：附件上传"内容优先"格式识别改造

> 状态：已实施（2026-09-11，测试 9/9 通过，前端类型检查通过）
> 日期：2026-09-11
> 涉及：后端 `AttachmentService`（主要）、前端错误提示（可选）

## 1. 背景与问题

用户上传 `1788069800213.jpg` 报"附件内容与文件格式不匹配"。

现流程（`AttachmentService.upload`）三道校验：

1. 扩展名白名单 `ALLOWED` + `matchesContentType`（依赖浏览器上报的 Content-Type）；
2. 魔数校验 `hasValidFileSignature`：要求文件头与扩展名**严格一致**（jpg 必须为 `FF D8 FF`）；
3. 任意一道不符即拒绝。

问题：大量真实场景的图片（微信保存图、网页下载图）是 **WebP/PNG 内容 + .jpg 扩展名**，被第 2 道校验拒绝。这类文件本身是安全的常见图片格式，拒绝体验差且无必要。

## 2. 目标与非目标

**目标**

- 声明扩展名与真实内容不一致的**受支持格式文件**（如 WebP 改名 .jpg）可正常上传；
- 落库、预览、下载按**真实格式**处理，前端行为不变；
- 安全性不降级：伪造扩展名的可执行内容（未知魔数、ZIP 包、OLE 安装包）仍被拒绝；
- 拒绝时给出可理解的错误信息。

**非目标**

- 不做格式转码（不把 WebP 转成 JPG，按真实格式原样存储）；
- 不放宽单文件 20 MB 限制与前端交互结构。

## 3. 方案对比

| 方案 | 思路 | 结论 |
|---|---|---|
| A. 放弃魔数校验 | 只看扩展名 | ❌ 安全降级，伪装可执行文件可上传 |
| B. 校验失败仅改报错文案 | 仍拒绝 | ❌ 未满足"接受这些文件"的诉求 |
| **C. 内容优先（推荐）** | 以魔数识别真实格式，按真实格式落库 | ✅ 兼容改名文件，安全性不降级 |

## 4. 详细设计（方案 C）

### 4.1 新增：真实格式识别

`AttachmentService` 新增私有方法：

```java
private record ActualFormat(String extension, String contentType) { }
private static ActualFormat detectActualFormat(Path file)
```

判定规则（按文件头字节 + 现有 `hasZipDirectory` 复用）：

| 文件头 | 判定 | 扩展名 / Content-Type |
|---|---|---|
| `FF D8 FF` | JPEG | `jpg` / `image/jpeg` |
| `89 50 4E 47 0D 0A 1A 0A` | PNG | `png` / `image/png` |
| `GIF87a` / `GIF89a` | GIF | `gif` / `image/gif` |
| `RIFF` + 偏移 8 `WEBP` | WebP | `webp` / `image/webp` |
| `%PDF-` | PDF | `pdf` / `application/pdf` |
| OLE 头 `D0 CF 11 E0 A1 B1 1A E1` | doc/xls 容器 | 见 4.2 特殊规则 |
| ZIP 目录含 `word/` | DOCX | `docx` / 对应 MIME |
| ZIP 目录含 `xl/` | XLSX | `xlsx` / 对应 MIME |
| 其余 | 无法识别 | 返回 null |

### 4.2 上传流程改造（`upload` 方法）

1. **取消** `matchesContentType` 对浏览器 Content-Type 的强校验（浏览器上报值本就不可靠、可伪造，作为安全依据无意义）。
2. 落盘临时文件后调用 `detectActualFormat`：
   - **图片与 PDF（4.1 前五行）**：无论声明扩展名是什么，一律接受；`storedName = UUID + 真实扩展名`，`contentType = 真实 MIME`；`originalName` 保留用户原始文件名不变。
   - **OLE 容器（doc/xls）**：二者魔数相同，无法靠内容区分，存在 MSI 等同头文件风险——**仅当声明扩展名为 `doc` 或 `xls` 时接受**，扩展名与 Content-Type 按声明值落库；否则拒绝，报"不支持的附件格式"。
   - **ZIP 容器**：按目录已可靠区分 docx/xlsx，按 4.1 接受；目录不含 `word/`、`xl/`（普通 zip 压缩包等）→ 拒绝，报"附件内容为 ZIP 压缩包，不在支持的格式内"。
   - **识别失败**（含空文件、文本、可执行等）→ 拒绝，报"无法识别附件内容格式，请确认文件完整或转换为受支持格式（PDF / Word / 图片等）"。
3. 原 `ALLOWED` 白名单保留，仅用于 OLE 分支的扩展名约束与常量维护。
4. `hasValidFileSignature`、`matchesContentType` 方法删除（逻辑并入 `detectActualFormat`）。

### 4.3 下游兼容性

- `AttachmentEntity`：构造时按传入 `contentType` 判定 `DIRECT` 预览——图片/PDF 按真实 MIME 落库后行为不变（浏览器均可直接渲染 WebP/PNG/GIF）。
- 下载：`originalName` 不变（用户看到 `xxx.jpg`），Content-Disposition 用原名，浏览器按真实 `contentType` 渲染/打开，无影响。
- 预览转换服务：非 DIRECT 类型逻辑不变。
- 数据库：无 schema 变更。

### 4.4 前端可选优化（建议一并做）

`RequirementEditor.vue` / `RequirementForm.vue` 上传失败目前固定提示"附件上传失败"，不透出后端 message。改为解析响应体 `message` 字段并展示（axios 错误处理），用户能直接看到"无法识别附件内容格式…"等具体原因。

## 5. 改动清单

| 文件 | 改动 |
|---|---|
| `backend/src/main/java/com/xqfx/requirements/requirement/AttachmentService.java` | 新增 `detectActualFormat`；重写校验与落库逻辑；删除两个旧校验方法 |
| `frontend/src/components/RequirementEditor.vue` | （可选）上传失败展示后端 message |
| `frontend/src/components/RequirementForm.vue` | （可选）同上 |
| `backend/src/test/java/com/xqfx/requirements/requirement/AttachmentServiceFormatTest.java` | 新增单元测试 |

## 6. 测试计划

新增 JUnit 单测（`@TempDir` 构造上传根目录，直接实例化 Service）：

1. PNG 内容 + `.jpg` 扩展名 → 上传成功，`storedName` 以 `.png` 结尾，`contentType=image/png`；
2. WebP 内容 + `.jpg` 扩展名 → 成功，落库 `image/webp`；
3. 真 JPEG → 成功，行为与现状一致；
4. 文本文件 + `.jpg` 扩展名 → 拒绝"无法识别附件内容格式"；
5. 空文件 → 拒绝；
6. 普通 ZIP 压缩包 + `.docx` 扩展名 → 拒绝"ZIP 压缩包"文案；
7. 真实 DOCX（zip 含 `word/`）+ 任意扩展名 → 成功；
8. OLE 内容 + `.exe` 扩展名 → 拒绝"不支持的附件格式"；
9. OLE 内容 + `.xls` 扩展名 → 成功。

## 7. 风险与回滚

- **风险**：魔数识别极端边界（截断文件、魔数正确但内容损坏）——损坏文件可能被接受存储，但预览/打开由查看端兜底，不构成安全问题；
- **回滚**：改动集中于单文件，直接 revert 即可，无数据迁移。
