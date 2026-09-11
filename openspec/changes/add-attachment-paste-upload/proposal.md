## Why

现状：填写需求（`RequirementForm.vue`）与编辑需求（`RequirementEditor.vue`）的附件区仅支持"点击选择文件"和"拖拽上传"两种入口。业务侧最常见的场景是——用户用 QQ / 微信 / Snipaste / Win+Shift+S 截图后想直接贴进来，或从 Word / 网页 / 邮件里复制一段包含图片的内容想同时把文字和图片带进来。当前用户必须先"另存为文件"再"点击选择"，中断心流、增加放弃率，也是过去两周内部反馈中出现频次最高的可用性缺陷。

## What Changes

- **新增粘贴入口**：填写需求页与编辑需求页的附件区，支持通过 Ctrl+V / ⌘+V / 右键粘贴 / Shift+Insert 触发附件添加，与已有的"点击 / 拖拽"入口共存。
- **支持三类粘贴源**：
  - **位图剪贴板**（截图工具、复制图片）——从 `clipboardData.items` 中取 `kind === 'file'` 的 `image/*` blob，自动生成语义化文件名（如 `截图-20260911-143207.png`），复用现有格式白名单（png/jpg/gif/webp）。
  - **文件剪贴板**（Windows 资源管理器 / Finder 复制的文件）——读取 `clipboardData.files`，走与拖拽完全一致的 `addFiles` 校验管线。
  - **富文本 HTML 剪贴板**（Word / 网页 / 邮件）——解析 `text/html`，抽取 `<img>` 中的 `data:image/*;base64,...` 内联图为附件；`http(s)://` 远程图默认**不下载**（避免 SSRF 与版权风险），改为在附件区顶部以 inline 提示条列出"检测到 N 张远程图片，请手动下载后上传"，可点击展开预览。文本部分不做自动填充，由用户主动粘贴到"需求内容"文本域。
- **粘贴监听范围**：整个表单容器（`<Form>` 根节点），而非仅附件 dropzone。原因：用户截图后习惯性直接按 Ctrl+V，不会先点附件区。当焦点位于`标题`、`需求内容`等文本输入控件时，粘贴行为需分流——纯文本仍走浏览器默认（不拦截），仅当剪贴板包含 `image/*` 或 `Files` 时才截获并加入附件队列，并在附件区做短暂高亮（focus ring）指示"已添加到附件"。
- **可访问性与反馈**：附件区提示文案更新为"拖拽、点击选择，或 Ctrl+V 粘贴（支持截图与文件）"；粘贴成功时以 Ant Design `message.success` 显示"已添加 N 个附件"，失败原因（格式不支持 / 超过 100MB / 重复）沿用现有 `message.warning` 文案。
- **上传路径不变**：粘贴产生的 `File` 对象继续走 `POST /api/requirements/{id}/attachments`，后端接口、`AttachmentService` 的魔数校验、预览生成、权限模型均无改动。
- **暂存 / 提交时机不变**：新增页仍在保存需求成功后统一上传附件（`uploadFiles(requirementId)`），编辑页仍在点击"上传附件"按钮后触发（`uploadAttachments()`）。粘贴只影响"文件进入待上传队列"这一步。

## Capabilities

### New Capabilities
- `requirement-attachments`: 需求附件的完整能力（生命周期、上传入口、校验、预览、权限）。本次为该 capability 首次建立 spec，delta 聚焦"粘贴上传"这一入口，同时把已有"点击 / 拖拽 / 校验 / 上传"作为背景 requirement 一并纳入，形成完整基线。

### Modified Capabilities
<!-- 无：openspec/specs 目录为空，本次不涉及对既有 spec 的修改 -->

## Impact

**受影响代码（前端）**
- `frontend/src/components/RequirementForm.vue`：新增 `paste` 事件监听、粘贴分流、剪贴板解析、富文本远程图提示条；提示文案更新。
- `frontend/src/components/RequirementEditor.vue`：同上，共享同一套粘贴解析逻辑。
- 新建 `frontend/src/composables/useClipboardAttachments.ts`：抽出可复用的剪贴板 → `File[]` 解析器（items / files / text-html 三路解析 + 自动命名 + 白名单校验），供上述两个组件调用，便于单元测试。
- `frontend/src/style.css`：附件区新增 `.attachment-dropzone.is-paste-target` 焦点高亮样式，`.attachment-remote-hint` 提示条样式。

**受影响代码（后端）**
- 无。粘贴产生的 `File` 对象与点击 / 拖拽产生的对象在前端合并进同一个 `selectedFiles: File[]` 队列，走既有 `POST /api/requirements/{id}/attachments`；`AttachmentService.detectActualFormat` 的魔数校验天然覆盖截图 PNG / JPG / WebP。

**API / 数据库 / 依赖**
- 无新增接口、无 schema 迁移、无第三方依赖新增。剪贴板 API（`ClipboardEvent.clipboardData`）为 Web 标准，Chrome / Edge / Firefox / Safari 全部支持 `items` + `files` + `getData('text/html')`。

**兼容性**
- 已有测试（`AttachmentServiceFormatTest`、前端 e2e 中"点击 / 拖拽"用例）保持不变、不受影响。
- 老浏览器降级：若 `ClipboardEvent.clipboardData` 不可用（IE、非常旧的 Safari），粘贴事件不触发附件添加，浏览器默认行为保留，不影响用户点击 / 拖拽。

**安全**
- 富文本 HTML 只做本地 DOMParser 解析（`text/html` → `DOMParser` with `image/svg+xml` 之外的独立文档），**不使用 `v-html`、不注入到当前文档**，杜绝 XSS。
- 远程图片 URL 不做后端代理下载，从根本上避免 SSRF、DNS rebinding、私网穿透等风险。
- 前端扩展名白名单 + 100MB 限制沿用；后端魔数校验作为最终防线（截图工具产出的 PNG 头部 `89 50 4E 47` 已被 `detectActualFormat` 识别）。

**开放问题（不阻塞本次交付，记录以便未来评估）**
1. 是否需要在编辑页也提供"从远程 URL 导入图片"按钮（走后端受控代理，白名单域名 + 大小限制）？本次默认不做。
2. 是否需要把粘贴的文本内容自动填入"需求内容"文本域？本次默认不做（浏览器默认行为已经能覆盖用户主动粘贴到 textarea 的场景）。
3. 是否记录"附件来源 = 粘贴 / 拖拽 / 点击"埋点用于后续体验分析？本次默认不做，避免侵入 `attachments` 表结构。
