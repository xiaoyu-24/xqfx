## Context

见 `proposal.md - Why`。本设计文档聚焦"如何实现"。

**当前代码约束**（不重述业务动机，仅列出影响技术选型的现状）：

- `frontend/src/components/RequirementForm.vue`（填写需求页）与 `frontend/src/components/RequirementEditor.vue`（编辑需求页）已经各自维护一份几乎相同的 `addFiles(files: File[])` / `selectFiles` / `dropFiles` / `selectedFiles` / `allowedAttachmentExtensions` / 100 MB 限制 / 重复文件检测逻辑，共约 30 行代码重复。
- 后端 `POST /api/requirements/{id}/attachments` 接收 `MultipartFile file`（form-data 字段名固定为 `file`），`AttachmentService.upload` 通过 `detectActualFormat`（魔数识别）做"内容优先"校验，扩展名与实际内容不符时按实际内容落库或拒绝（详见 `.workbuddy/memory/2026-09-11.md`）。
- `spring.servlet.multipart.max-file-size: -1`（不限制），前端 100 MB 限制是应用层策略。
- Ant Design Vue `message` 已在两处组件中作为反馈渠道；`apiErrorDetails` 已统一封装后端错误 message 的透出。
- 无第三方剪贴板库；项目 `package.json` 目前不含 `clipboard-polyfill`、`paste-image` 之类的依赖。

## Goals / Non-Goals

**Goals（设计层面，超出 proposal 范围的技术目标）：**

- 消除两处组件的附件校验代码重复——本次借助粘贴能力的引入，抽出可复用 composable，为将来的"分片上传"、"附件来源埋点"等能力预留单点扩展位。
- 保证粘贴解析逻辑可独立单元测试（jsdom 中可 mock `ClipboardEvent`），不依赖真实浏览器剪贴板。
- 保证粘贴路径的失败模式（浏览器不支持、items 为空、getAsFile 返回 null、DOMParser 抛错）不产生用户可见的错误噪音，静默降级到"什么都没发生"。
- 保证 XSS / SSRF 攻击面**零新增**：不引入 `v-html`、不发起对剪贴板中 URL 的任何网络请求、不将解析后的 DOM 片段插入当前文档。

**Non-Goals（本次设计明确不做）：**

- **不做**远程 URL 图片的后端代理下载（涉及 SSRF 白名单、DNS rebinding 防护、Referer 处理、 robots.txt 遵守、版权与合规审查，工作量与风险都远超本次范围）。
- **不做**粘贴图片的客户端编辑（裁剪、标注、马赛克）——独立立项。
- **不做**粘贴文本自动填入"需求内容"（浏览器默认行为已覆盖）。
- **不做**附件来源埋点（`source: paste | drop | click`）到数据库——避免侵入 `attachments` 表结构，未来若需要可另立 change。
- **不做**分片上传 / 秒传 / 断点续传——现有 100 MB 单文件限制下不需要。
- **不做**IE 兼容——项目已明确不支持 IE。

## Decisions

### D1：抽出 `useClipboardAttachments` composable

**决定**：新建 `frontend/src/composables/useClipboardAttachments.ts`，导出：

```ts
export interface ClipboardParseResult {
  files: File[]            // 已加时间戳命名的 File 对象（位图 + 文件 + HTML data URL）
  remoteImageUrls: string[] // 富文本中的 http(s)/file/cid URL 列表，仅用于提示条
  rejectedReasons: string[] // 聚合的拒绝原因（用于 message.warning）
}

export function useClipboardAttachments(options: {
  allowedExtensions: Set<string>
  maxFileSizeBytes: number
}): {
  parseClipboard: (event: ClipboardEvent) => Promise<ClipboardParseResult>
  isPasteSupported: () => boolean
}
```

`RequirementForm.vue` 与 `RequirementEditor.vue` 各自调用 `parseClipboard(event)`，然后把 `result.files` 送入现有的 `addFiles(...)`（复用去重、聚合提示、队列追加逻辑）。

**Alternatives considered**：

- **A：每处组件复制粘贴逻辑**——违反 DRY，未来两处组件的粘贴行为容易漂移；测试成本翻倍。**拒绝**。
- **B：抽公共 `<AttachmentUpload>` 组件包住整个附件区**——侵入更大，会破坏现有 e2e 测试对 `data-test="attachment-dropzone"` / `data-test="edit-attachment-dropzone"` 的定位；两处附件区的 UI 布局有细微差异（编辑页有"上传附件"按钮，填写页没有），强行统一得不偿失。**拒绝**。
- **C：composable（选中）**——最小侵入，两处组件的模板结构保持原样，只在 `<script setup>` 中多两行调用；单元测试可脱离组件运行。**采纳**。

### D2：粘贴监听范围 = 表单容器根节点 + 焦点分流

**决定**：在 `<Form>` 根节点（填写页的 `.requirement-form`、编辑页的 `.requirement-editor-form`）绑定 `@paste="onPaste"`，而非 `document` 或附件区。

`onPaste` 内部分流逻辑：

```
1. 若 event.target 位于当前表单容器之外（例如 Ant Modal 通过 teleport 挂到 body）→ return
2. 若 clipboardData 不含 image/* / Files / text-html 中的 <img> → return（保留浏览器默认文本粘贴）
3. 否则 event.preventDefault() + 调用 parseClipboard + addFiles + 高亮附件区
```

**Alternatives considered**：

- **A：只监听附件区 dropzone**——用户必须先点击附件区才能粘贴，与"截图后条件反射按 Ctrl+V"的用户心智不符，可用性大打折扣。**拒绝**。
- **B：监听 `document`**——会拦截 Ant Modal / Popover / Select 下拉等浮层内的粘贴（例如"填写处理情况"弹窗内的 textarea），造成回归。**拒绝**。
- **C：表单容器 + 焦点分流（选中）**——精准命中目标区域，浮层因 `teleport` 挂到 `body` 天然被排除；分流逻辑保证纯文本粘贴不受影响。**采纳**。

**边界处理**：Ant Design Vue 的 `Modal`、`Popover`、`Select` 下拉默认通过 `teleport` 挂载到 `document.body`，`event.target` 不会落入表单容器；`Tooltip`、`Dropdown` 的触发器仍在表单内，但其浮层内容不含可编辑控件，不构成分流冲突。

### D3：远程图片"只提示、不下载"

**决定**：`parseClipboard` 解析 `text/html` 时，将所有 `<img src>` 按协议分类：

| 协议 | 处理 |
|------|------|
| `data:image/{png,jpeg,gif,webp};base64,...` | 解码为 `File`，加入 `files` 数组 |
| `data:image/svg+xml;...` | 拒绝（XSS 风险），纳入 `rejectedReasons` |
| `http://` / `https://` | 加入 `remoteImageUrls`，**不发起请求** |
| `file://` / `cid:` / `ftp://` / 其他 | 加入 `remoteImageUrls`，附加标注"（浏览器安全策略不允许访问）" |
| 相对 URL / 空 src | 忽略 |

调用方（组件）根据 `remoteImageUrls.length > 0` 显示可关闭的 `<Alert type="info">` 提示条，位置在附件区上方；提示条内的 URL 列表通过 `<span v-for="url in remoteImageUrls">{{ url }}</span>` 渲染为纯文本，**不使用 `v-html`、不渲染为 `<img>` 或 `<a>`**。

**Alternatives considered**：

- **A：后端加 `POST /api/attachments/fetch-remote` 代理下载**——引入 SSRF 面（需要域名白名单 / 私网 IP 拒绝 / DNS rebinding 防护 / 重定向跟随策略），且涉及版权问题（自动抓取第三方图片）。工作量与安全审计成本远超本次范围。**拒绝**（记录在 proposal 开放问题 1）。
- **B：前端 `fetch(url)` 直接下载**——CORS 会拒绝绝大多数跨域图片；即使成功，`file://` / `cid:` 也访问不了；且从用户浏览器发起请求会带 Referer / Cookie，产生隐私泄露。**拒绝**。
- **C：只提示（选中）**——零风险、实现简单、用户教育意义明确。**采纳**。

### D4：HTML 解析用 `DOMParser` 独立文档

**决定**：

```ts
const doc = new DOMParser().parseFromString(html, 'text/html')
const imgs = doc.querySelectorAll('img[src]')
```

解析产生的 `Document` 与当前页面**完全隔离**：`<script>` 不执行、`<img>` 不加载（因为不在活文档中，浏览器不会触发资源请求）、事件监听器不生效。

**Alternatives considered**：

- **A：正则匹配 `/<img[^>]+src=["']([^"']+)["']/gi`**——脆弱（无法处理属性顺序、CDATA、注释中的伪 img、编码变体），且攻击者可构造绕过。**拒绝**。
- **B：`document.createElement('div'); div.innerHTML = html`**——**严重 XSS 风险**：`<img onerror="...">`、`<script>`（在某些情况下）会立即执行；即便不执行，`<img src="http://...">` 也会触发资源请求，泄露用户 IP 与 Referer。**拒绝**。
- **C：`DOMParser`（选中）**——W3C 标准，规范明确规定解析产物为"inert document"（不加载资源、不执行脚本、不触发 CSS 计算）。Chrome / Firefox / Safari / Edge 全部支持。**采纳**。

### D5：文件命名策略

**决定**：

| 来源 | 命名格式 | 示例 |
|------|---------|------|
| `clipboardData.items` 位图 blob | `截图-YYYYMMDD-HHmmss.<ext>` | `截图-20260911-143207.png` |
| 同一次粘贴多张位图 | `截图-YYYYMMDD-HHmmss-<index>.<ext>` | `截图-20260911-143207-1.png`、`截图-20260911-143207-2.png` |
| HTML data URL 内联图 | `粘贴图片-YYYYMMDD-HHmmss-<index>.<ext>` | `粘贴图片-20260911-143207-1.png` |
| `clipboardData.files`（复制的文件） | **保留原文件名** | `需求说明书.pdf` |

时间戳统一使用 Asia/Shanghai 时区（与后端 `hibernate.jdbc.time_zone` 一致），通过 `dayjs().format('YYYYMMDD-HHmmss')` 生成（项目已依赖 `dayjs`）。

**Alternatives considered**：

- **A：UUID**——用户看不懂，无法从文件名判断粘贴时间。**拒绝**。
- **B：固定名 `image.png`**——多次粘贴会同名，虽然有 `lastModified` 去重，但浏览器为 blob 分配的 `lastModified` 是"当前时间戳"，每次不同，去重不生效，用户会看到一堆 `image.png`。**拒绝**。
- **C：时间戳 + index（选中）**——可读、有序、多次粘贴可区分（精确到秒 + index）。**采纳**。

**边界处理**：若同一秒内两次粘贴，会产生同名文件；由于 `lastModified` 不同，去重不生效，队列中会有两个同名条目。这是可接受的（后端会用 UUID 落盘），且用户能从"待上传队列"看到重复并手动移除。

### D6：`DataTransferItem.getAsFile()` 必须同步调用

**决定**：`parseClipboard` 内部对 `event.clipboardData.items` 的遍历必须在事件回调的**同步执行阶段**完成，`getAsFile()` 立即调用；不允许先 `await` 什么再回来取。

```ts
// ✅ 正确
const blobs: Blob[] = []
for (const item of event.clipboardData.items) {
  if (item.kind === 'file' && item.type.startsWith('image/')) {
    const f = item.getAsFile()  // 同步
    if (f) blobs.push(f)
  }
}
// 之后再 await 转 File、命名等
```

**理由**：Firefox（尤其是 90 之前的版本）与部分 Safari 版本在 `paste` 事件回调返回后**清空** `DataTransferItemList`，异步回调中 `getAsFile()` 返回 `null`。这是 W3C 规范允许的浏览器行为。

**Alternatives considered**：

- **A：全异步管线**——在 Firefox 上直接失效。**拒绝**。
- **B：同步收集 Blob 后再异步处理（选中）**——所有浏览器都能工作。**采纳**。

### D7：单次粘贴的数量与总大小上限

**决定**：单次粘贴事件产生的文件数上限 = **20 个**；单次粘贴 base64 解码前的 HTML 字符串长度上限 = **10 MB**（大约对应 7.5 MB 的原始二进制）。超过则拒绝并 message 提示"单次粘贴内容过多，请分批操作"。

**理由**：

- 20 个上限：Word 文档粘贴可能带出大量装饰性小图（项目符号、分隔线），无上限会导致待上传队列爆炸，用户滚动困难；20 是"合理业务量"与"防误伤"的折中。
- 10 MB HTML 上限：base64 解码是 CPU 密集操作，超大 HTML 会阻塞主线程数秒；10 MB 大约对应 500 页 Word 文档，超过此量的粘贴几乎必然是误操作或恶意输入。

**Alternatives considered**：

- **A：无上限**——DoS 风险（用户自己的浏览器卡死也是糟糕体验）。**拒绝**。
- **B：更严格（如 5 个）**——正常业务场景（例如粘贴一份含 8 张截图的巡检报告）会被误伤。**拒绝**。
- **C：20 个 / 10 MB（选中）**——覆盖 95% 以上真实场景，同时防御异常输入。**采纳**。

### D8：测试策略

**决定**：

- **单元测试**（新增）：`frontend/src/composables/__tests__/useClipboardAttachments.spec.ts`，使用 Vitest + jsdom（若项目当前无 Vitest，则新增为 dev 依赖；已有则复用）。覆盖：位图 blob 提取、文件列表提取、HTML data URL 解码、SVG 拒绝、远程 URL 收集、Firefox 同步/异步行为、DOMParser 抛错降级、单次粘贴数量上限。
- **组件测试**（可选，若时间允许）：`RequirementForm.spec.ts` 增加"粘贴触发 addFiles"用例。
- **E2E 测试**（新增）：`frontend/e2e/attachment-paste.spec.ts`（若项目已有 Playwright，则复用）。Playwright 支持通过 `page.evaluate` 手动派发 `ClipboardEvent`（因为浏览器安全策略禁止 E2E 工具直接写入系统剪贴板），或使用 `page.context().grantPermissions(['clipboard-read', 'clipboard-write'])` + `navigator.clipboard.write`（Chromium 支持）。
- **回归测试**：现有"点击 / 拖拽"用例保持不变，全部通过后方可合并。

**Alternatives considered**：

- **A：只做手工测试**——粘贴解析边界情况多（Firefox items 失效、SVG 拒绝、DOMParser 抛错），手工难以覆盖。**拒绝**。
- **B：单元 + E2E（选中）**——composable 单元测试保证解析逻辑正确，E2E 保证组件集成不退化。**采纳**。

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Safari 早期版本 `clipboardData.items` 不包含位图 blob | 用户从截图工具粘贴时静默失败 | 提供降级路径：Safari 用户仍可用"点击选择"和"拖拽"；提示条文案"支持 Ctrl+V 粘贴截图"仅在 `isPasteSupported()` 返回 true 时显示 |
| Firefox `DataTransferItemList` 在异步回调中失效 | `getAsFile()` 返回 null，粘贴无效 | D6 中已强制同步遍历；单元测试用 Firefox-like mock 覆盖此路径 |
| 用户从含大量装饰性小图的 Word 文档粘贴 | 待上传队列涌入 20+ 无意义小图 | D7 单次粘贴 20 个上限 + 用户在"待上传队列"可逐个移除；提示条引导 |
| 富文本粘贴时提示条遮挡表单其他字段 | 视觉噪音、影响填写效率 | 提示条使用 Ant Design `<Alert closable>`，用户可关闭；下次粘贴事件也会重置 |
| base64 解码大图片阻塞主线程 | 页面卡顿数秒 | D7 HTML 字符串 10 MB 上限；未来若需要可引入 Web Worker（本次不做，避免过度设计） |
| 用户在 Modal 内粘贴时事件冒泡到表单容器 | 干扰浮层内的正常粘贴 | D2 焦点分流：`event.target` 不在表单容器内直接 return |
| Ant Design Vue `message` 频繁弹出造成视觉疲劳 | 用户忽略重要提示 | 拒绝原因聚合为一条 message（沿用现有行为）；成功 message 1.5 秒自动消失 |
| 前端 100 MB 限制与后端 `max-file-size: -1` 不一致 | 用户粘贴 200 MB 文件时前端拦截，但后端理论上接受 | 保持现有限制不变（本次不修改 100 MB 阈值）；proposal 开放问题外的既定业务策略 |

**Trade-off：粘贴范围"整个表单"vs"只附件区"**

- 选"整个表单"：用户友好（截图后条件反射 Ctrl+V 就能命中），但需要焦点分流逻辑，实现复杂度略高，且必须处理 Modal / Popover 冒泡。
- 选"只附件区"：实现简单、零风险，但用户必须先点击附件区才能粘贴，可用性大打折扣。
- **决策**：接受复杂度换取可用性。焦点分流逻辑集中在 composable 内，可单元测试覆盖。

**Trade-off：远程图片"只提示"vs"代理下载"**

- 选"只提示"：零安全风险、实现简单，但用户需要多一步"右键另存为"。
- 选"代理下载"：用户体验流畅，但引入 SSRF / 版权 / 合规三重风险，且需要新增后端接口 + 白名单配置 + 审计日志。
- **决策**：本次选"只提示"，把"代理下载"留作未来独立立项（proposal 开放问题 1）。

## Migration Plan

- **无 schema 变更**：`attachments` 表结构不变；无 Flyway 迁移。
- **无 API 变更**：`POST /api/requirements/{id}/attachments` 接口签名与行为完全不变。
- **无后端代码变更**：`AttachmentService` / `AttachmentController` / `RequirementController` 均不修改。
- **前端发布**：合并到主分支后，通过现有前端构建流水线发布；用户下次刷新页面即生效。
- **回滚策略**：若上线后发现严重问题，回滚前端 commit 即可；因为后端与数据库无变化，回滚零成本、零数据风险。
- **灰度**：本次变更影响面小（仅前端两处组件 + 一个新 composable），不需要灰度发布；若团队要求，可通过环境变量 `VITE_ENABLE_PASTE_UPLOAD=true/false` 控制 composable 是否注册 `paste` 监听器（可选，不作为强制要求）。

## Open Questions

以下问题**不影响本次 spec / design / tasks 的完整性**，可延后决策：

1. **提示条"查看链接"是否需要"一键复制全部 URL"按钮？** —— 当前设计只提供逐个 `<span>` 展示，用户手动选中复制。若真实使用中反馈"太麻烦"，可后续追加。
2. **是否为粘贴的截图提供轻量编辑（裁剪 / 标注 / 马赛克）？** —— 需要引入图片编辑库（如 `tui-image-editor`），本次不做，独立立项。
3. **是否引入 Web Worker 处理超大 base64 解码？** —— D7 已用 10 MB 上限规避主线程阻塞；若真实场景需要更大上限，再引入 Worker。
4. **是否在 `attachments` 表增加 `source` 列（`paste` / `drop` / `click`）用于埋点分析？** —— proposal 开放问题 3，本次不做，避免 schema 变更。
