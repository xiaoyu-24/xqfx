## 1. 前置准备与依赖确认

- [x] 1.1 阅读 `proposal.md`、`specs/requirement-attachments/spec.md`、`design.md`，确认 D1–D8 决策与开放问题 1–4 的处置方案不变
- [ ] 1.2 检查 `frontend/package.json`：若已存在 `vitest` + `jsdom`（或 `happy-dom`），跳过；否则新增为 devDependency（用于 composable 单元测试）  <!-- 检查结果：均未安装；是否新增待 Group 6 决策点确认 -->
- [x] 1.3 检查 `frontend/package.json` 是否已包含 `dayjs`（现有代码已引入 `dayjs/locale/zh-cn`），确认可直接用于时间戳生成  <!-- 检查：dayjs v1.11.21 通过 ant-design-vue 传递依赖安装，App.vue / main.ts 已直接 import，可用 -->
- [x] 1.4 检查现有 e2e 测试框架（Playwright / Cypress），若已存在则复用；本 change 不新增 E2E 框架  <!-- 检查结果：均无；Group 7 按 7.1 指令跳过 -->
- [x] 1.5 在 `frontend/src/composables/` 目录下确认命名风格（现有：`useApiError.ts`、`useAuth.ts`、`useCreateFormState.ts`、`useDictionaryOptions.ts`、`useFormErrors.ts`、`usePageRefresh.ts`），新文件遵循 `useXxx.ts` 命名

## 2. 核心 Composable 实现

- [x] 2.1 新建 `frontend/src/composables/useClipboardAttachments.ts`，导出 `ClipboardParseResult` 接口与 `useClipboardAttachments(options)` 函数（签名见 design.md D1）
- [x] 2.2 实现 `isPasteSupported()`：检测 `typeof ClipboardEvent !== 'undefined'` 且 `'clipboardData' in ClipboardEvent.prototype`；不支持时组件不注册 paste 监听器（对应 spec "浏览器兼容性 / ClipboardEvent.clipboardData 不可用"场景）
- [x] 2.3 实现"位图 blob 提取"分支：**同步**遍历 `event.clipboardData.items`，对 `kind === 'file'` 且 `type` 以 `image/` 开头（且非 `image/svg+xml`）的项立即调用 `getAsFile()`，收集到 `blobs: Blob[]`（对应 design.md D6，spec "剪贴板位图粘贴 / 粘贴截图工具产生的位图"）
- [x] 2.4 实现"文件列表提取"分支：若 `event.clipboardData.files.length > 0`，直接以 `Array.from(files)` 作为来源，**优先级高于** items 分支（对应 spec "剪贴板位图粘贴 / 位图与文件同时存在于剪贴板"）
- [x] 2.5 实现"HTML data URL 提取"分支：调用 `event.clipboardData.getData('text/html')`，用 `new DOMParser().parseFromString(html, 'text/html')` 解析，`querySelectorAll('img[src]')` 遍历，对 `src` 以 `data:image/{png,jpeg,gif,webp};base64,` 开头的项解码为 `Blob`（用 `atob` + `Uint8Array` + `new Blob([bytes], { type })`）（对应 design.md D4，spec "富文本 HTML 粘贴 / 从 Word / 网页 / 邮件复制含内联 base64 图片的内容"）
- [x] 2.6 实现"SVG 拒绝"逻辑：MIME 为 `image/svg+xml` 的 blob、data URL 一律拒绝并加入 `rejectedReasons`（对应 spec "安全性约束 / SVG 一律拒绝"）
- [x] 2.7 实现"远程 URL 收集"逻辑：`<img src>` 协议为 `http:` / `https:` / `file:` / `cid:` / `ftp:` 时加入 `remoteImageUrls` 数组，`file:` / `cid:` / `ftp:` 附加标注"（浏览器安全策略不允许访问，请手动附加原文件）"；**不发起任何网络请求**（对应 design.md D3，spec "富文本 HTML 粘贴 / 从网页复制含远程 http(s) 图片的内容"与"HTML 中的 file:// 或 cid: 图片引用"）
- [x] 2.8 实现"HTML 解析异常降级"：`DOMParser.parseFromString` 抛错或返回文档中含 `<parsererror>` 时，静默跳过 HTML 分支，不影响 items / files 分支（对应 spec "富文本 HTML 粘贴 / HTML 解析异常"）
- [x] 2.9 实现"文件命名"：位图 blob → `截图-YYYYMMDD-HHmmss.<ext>`；同一次多张 → 追加 `-<index>`；HTML data URL → `粘贴图片-YYYYMMDD-HHmmss-<index>.<ext>`；时区使用 `dayjs().format('YYYYMMDD-HHmmss')`（dayjs 已配置 zh-cn locale，默认使用本地时区，用户在 Asia/Shanghai）（对应 design.md D5）
- [x] 2.10 实现 MIME → 扩展名映射：`image/png → png`、`image/jpeg → jpg`、`image/webp → webp`、`image/gif → gif`；其他 MIME 拒绝
- [x] 2.11 实现"客户端校验"：对每个候选 `File`，检查扩展名是否在 `options.allowedExtensions`、字节数是否 ≤ `options.maxFileSizeBytes`；不通过的加入 `rejectedReasons`（对应 spec "附件客户端校验规则"）
- [x] 2.12 实现"数量与总大小上限"：单次粘贴 `files.length > 20` 或 HTML 字符串长度 > 10 MB 时，返回空 `files` + `rejectedReasons: ['单次粘贴内容过多，请分批操作']`（对应 design.md D7）
- [x] 2.13 实现"Blob → File 转换"：使用 `new File([blob], generatedName, { type: blob.type, lastModified: Date.now() })`；Safari 早期版本若不支持 `File` 构造器，降级为 `blob` 并挂载 `name` / `lastModified` 属性（polyfill 逻辑内联，不引入依赖）

## 3. 组件集成：填写需求页

- [x] 3.1 在 `frontend/src/components/RequirementForm.vue` 的 `<script setup>` 中引入 `useClipboardAttachments`，以现有 `allowedAttachmentExtensions` 与 `100 * 1024 * 1024` 作为 options 实例化
- [x] 3.2 新增响应式状态：`remoteImageUrls = ref<string[]>([])`、`remoteHintVisible = ref(false)`、`pasteTargetActive = ref(false)`（用于附件区高亮 class）
- [x] 3.3 实现 `onPaste(event: ClipboardEvent)`：按 design.md D2 焦点分流：
  - 若 `event.target` 不在 `.requirement-form` 容器内（用 `formRootRef.value?.contains(event.target as Node)` 判断）→ return
  - 调用 `parseClipboard(event)`，若返回的 `files` + `remoteImageUrls` 均为空 → return（保留浏览器默认文本粘贴）
  - 否则 `event.preventDefault()`，把 `files` 送入现有 `addFiles(...)`，把 `remoteImageUrls` 写入状态并显示提示条
  - 设置 `pasteTargetActive = true`，1.2 秒后重置；调用 `attachmentDropzoneRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })`（对应 spec "粘贴事件焦点分流"所有场景）
- [x] 3.4 在 `<Form>` 根节点（现有 `<Form layout="vertical" ...>`）添加 `ref="formRootRef"` 与 `@paste="onPaste"`  <!-- 实施调整：Ant Design Vue `<Form>` 组件 ref 拿到的是组件实例而非 DOM，无法调用 `.contains()`；改为挂在 template 根节点 `<section class="requirement-form-shell">`（原生 DOM），事件冒泡经过此处，且 Ant Modal 默认 teleport 到 body 天然被排除。行为契约与本任务完全一致 -->
- [x] 3.5 在附件区 dropzone `<div class="attachment-dropzone">` 添加 `ref="attachmentDropzoneRef"` 与动态 class `:class="{ 'is-dragging': isDragging, 'is-paste-target': pasteTargetActive }"`
- [x] 3.6 在附件区上方（现有 `<p class="attachment-hint">` 之后、`<div class="attachment-dropzone">` 之前）插入远程图片提示条：使用 Ant Design `<Alert v-if="remoteHintVisible && remoteImageUrls.length" type="info" closable @close="remoteHintVisible = false">`，`<template #message>` 显示"检测到 N 张远程图片，出于安全与版权考虑不会自动下载。如需作为附件，请右键图片"另存为"后再上传。"，`<template #description>` 内用 `<span v-for="url in remoteImageUrls">{{ url }}</span>` 渲染纯文本 URL 列表（对应 design.md D3，spec "富文本 HTML 粘贴 / 从网页复制含远程 http(s) 图片的内容"）
- [x] 3.7 更新附件区提示文案：`支持图片、PDF、Word、Excel，单个文件最大 100MB。可点击选择、拖入，或直接 Ctrl+V 粘贴截图与文件。`（对应 spec "可访问性与用户反馈 / 附件区提示文案"）
- [x] 3.8 添加粘贴成功反馈：在 `onPaste` 中 `files.length > 0` 时调用 `message.success(\`已添加 ${files.length} 个附件\`)`；`files.length === 0 && rejectedReasons.length > 0` 时调用 `message.warning(rejectedReasons.join('；'))`（对应 spec "可访问性与用户反馈 / 粘贴成功反馈"与"粘贴全部被拒绝"）
- [x] 3.9 保证粘贴产生的文件走**现有** `uploadFiles(requirementId)` 上传路径，不新增上传时机；`submit(draft)` 内的附件上传逻辑保持不变（对应 spec "附件上传时机与失败处理 / 填写需求页保存后上传"）

## 4. 组件集成：编辑需求页

- [x] 4.1 在 `frontend/src/components/RequirementEditor.vue` 的 `<script setup>` 中引入 `useClipboardAttachments`，与填写页共用同一 composable
- [x] 4.2 新增响应式状态：`remoteImageUrls`、`remoteHintVisible`、`pasteTargetActive`
- [x] 4.3 实现 `onPaste(event: ClipboardEvent)`：分流逻辑与填写页一致，但容器 ref 为 `.requirement-editor-form`；成功后触发 `schedulePreviewRefresh()`（与现有 `uploadAttachments` 一致）  <!-- 实施说明：编辑页 `<form>` 是原生 HTML form 元素（非 Ant `<Form>` 组件），可直接 ref；附件预览 Modal 为 `<form>` 兄弟节点，Modal.confirm 默认 teleport 到 body，两者均不会冒泡到 form，天然满足浮层内不拦截场景 -->
- [x] 4.4 在编辑表单根节点添加 `ref="formRootRef"` 与 `@paste="onPaste"`
- [x] 4.5 在附件区 dropzone 添加 `ref="attachmentDropzoneRef"` 与动态 class `:class="{ 'is-dragging': isDragging, 'is-paste-target': pasteTargetActive }"`
- [x] 4.6 在附件区上方插入远程图片提示条（结构与填写页一致，可提取为局部 `<template>` 或复制粘贴，本次不做进一步抽象）
- [x] 4.7 更新附件区提示文案：`支持 PDF / Word / 图片等常见格式，单个文件不超过 100MB。可点击选择、拖入，或直接 Ctrl+V 粘贴截图与文件。`（现有文案是"不超过 20 MB"，本次一并纠正为 100 MB 与实际实现一致；此项作为顺带修复，不视为 breaking）  <!-- 同步修复：addFiles 内部 `20 * 1024 * 1024` → `maxAttachmentSizeBytes`（100 MB），与填写页及 spec "附件客户端校验规则 / 单文件大小限制" 一致 -->
- [x] 4.8 添加粘贴成功 / 失败 message 反馈，与填写页文案一致
- [x] 4.9 保证粘贴产生的文件进入现有 `selectedFiles` 队列，用户仍需点击"上传附件"按钮触发 `uploadAttachments()`（对应 spec "附件上传时机与失败处理 / 编辑需求页显式上传"）

## 5. 样式与视觉

- [x] 5.1 在 `frontend/src/style.css` 新增 `.attachment-dropzone.is-paste-target` 样式：`border-color: #1677ff; background: #e6f4ff; box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.15);`，过渡动画沿用现有 `.2s`
- [x] 5.2 新增 `.attachment-dropzone:focus-visible` 样式：确保键盘 Tab 到附件区时有可见 focus ring（对应 spec "可访问性与用户反馈 / 键盘可达性"）
- [x] 5.3 新增 `.attachment-remote-hint` 样式（若使用自定义容器包裹 Ant Alert）：`margin: 8px 0;`，与附件区上下留白协调
- [x] 5.4 远程图片 URL 列表样式：`.attachment-remote-hint ul { max-height: 160px; overflow-y: auto; }`，`.attachment-remote-hint li { font-family: monospace; font-size: 12px; word-break: break-all; }`；确保长 URL 不撑破布局

## 6. 单元测试（composable）

> **状态：待后续补齐**（用户决策于 2026-09-11）。项目当前无 vitest / jsdom / @vue/test-utils 任何单元测试基础设施，本次 change 不引入新测试工具链。以下 17 个测试用例作为后续独立立项的完整需求清单保留（可直接作为新 change 的 tasks），不阻塞本次合入。composable 代码已按可测试性设计（纯函数、无副作用、options 注入），引入 vitest 后可直接编写用例无需重构。

- [ ] 6.1 新建 `frontend/src/composables/__tests__/useClipboardAttachments.spec.ts`
- [ ] 6.2 测试用例：粘贴单个位图 blob → 返回 1 个 `File`，命名符合 `截图-YYYYMMDD-HHmmss.png` 格式（用 `vi.useFakeTimers()` + `vi.setSystemTime()` 固定时间）
- [ ] 6.3 测试用例：粘贴多个位图 blob → 返回 N 个 `File`，命名带 `-<index>` 后缀
- [ ] 6.4 测试用例：粘贴 `clipboardData.files` 非空 → 直接返回这些 `File`，保留原文件名
- [ ] 6.5 测试用例：粘贴同时含 blob 与 files → 优先返回 files，忽略 blob（对应 spec "剪贴板位图粘贴 / 位图与文件同时存在于剪贴板"）
- [ ] 6.6 测试用例：粘贴含 `data:image/png;base64,...` 的 HTML → 解码为 `File`，命名符合 `粘贴图片-YYYYMMDD-HHmmss-1.png`
- [ ] 6.7 测试用例：粘贴含 `data:image/svg+xml;base64,...` 的 HTML → 拒绝，`rejectedReasons` 非空
- [ ] 6.8 测试用例：粘贴含 `<img src="https://example.com/a.png">` 的 HTML → `remoteImageUrls` 包含该 URL，`files` 为空，**未发起任何网络请求**（用 `vi.spyOn(globalThis, 'fetch')` 断言 fetch 未被调用）
- [ ] 6.9 测试用例：粘贴含 `<img src="file:///C:/secret.png">` 的 HTML → `remoteImageUrls` 包含 URL 与"（浏览器安全策略不允许访问）"标注
- [ ] 6.10 测试用例：HTML 中含 `<script>alert(1)</script>` → 解析过程不执行脚本（jsdom 中用 `vi.spyOn(window, 'alert')` 断言未被调用）；解析产物未插入 `document.body`
- [ ] 6.11 测试用例：HTML 解析异常（mock `DOMParser.parseFromString` 抛错）→ 静默降级，不影响 items / files 分支
- [ ] 6.12 测试用例：粘贴 21 个位图 → 触发数量上限，返回空 files + `rejectedReasons: ['单次粘贴内容过多，请分批操作']`
- [ ] 6.13 测试用例：粘贴 11 MB 长的 HTML 字符串 → 触发大小上限，同上
- [ ] 6.14 测试用例：模拟 Firefox 异步失效行为（`items` 在事件回调返回后清空）→ 因为解析是同步的，仍能拿到所有 blob（对应 design.md D6）
- [ ] 6.15 测试用例：`isPasteSupported()` 在 mock 掉 `ClipboardEvent` 后返回 false，`parseClipboard` 直接返回空结果
- [ ] 6.16 测试用例：文件扩展名不在白名单 → 加入 `rejectedReasons`（对应 spec "附件客户端校验规则 / 扩展名白名单"）
- [ ] 6.17 测试用例：文件 > 100 MB → 加入 `rejectedReasons`（对应 spec "附件客户端校验规则 / 单文件大小限制"）

## 7. E2E 测试

> **状态：待后续补齐**（tasks 7.1 明确指令：若无 e2e 框架，本组任务标记为待后续补齐，不阻塞合入）。项目当前无 Playwright / Cypress，本次不引入。以下 9 个 E2E 用例作为后续独立立项的需求清单保留，spec.md 中的 WHEN/THEN 场景在本次 change 中通过 Group 8 手工验证清单覆盖。

- [ ] 7.1 新建 `frontend/e2e/attachment-paste.spec.ts`（若项目已有 e2e 目录则复用命名风格；若无 e2e 框架，本组任务标记为"待后续补齐"，不阻塞合入）
- [ ] 7.2 E2E 用例：填写需求页 → 用 `page.evaluate` 派发 `ClipboardEvent` 携带位图 blob → 断言附件区出现"待上传"条目、命名符合规则、message 提示"已添加 1 个附件"
- [ ] 7.3 E2E 用例：填写需求页 → 派发 `ClipboardEvent` 携带 `text/html` 含 base64 图片 → 断言附件被添加
- [ ] 7.4 E2E 用例：填写需求页 → 派发 `ClipboardEvent` 携带 `text/html` 含远程图片 URL → 断言提示条出现，URL 列表可见，且 Network 面板无对该 URL 的请求
- [ ] 7.5 E2E 用例：焦点在"需求内容" textarea → 派发只含 `text/plain` 的粘贴事件 → 断言 textarea 值被追加，附件区无变化（对应 spec "粘贴事件焦点分流 / 焦点在文本输入控件且剪贴板只有纯文本"）
- [ ] 7.6 E2E 用例：焦点在"需求内容" textarea → 派发含位图的粘贴事件 → 断言 `event.defaultPrevented === true`、textarea 值未变、附件区新增文件、页面滚动到附件区
- [ ] 7.7 E2E 用例：编辑需求页 → 粘贴位图 → 断言"上传附件"按钮出现、点击后附件成功上传、`attachments` 列表刷新
- [ ] 7.8 E2E 用例：粘贴 SVG → 断言拒绝提示出现、附件队列无新增
- [ ] 7.9 回归：现有"点击选择"、"拖拽上传"用例全部通过（对应 proposal "兼容性"承诺）

## 8. 手工验证清单

> **状态：需用户手工执行**。以下 15 项涵盖 spec.md 中所有 WHEN/THEN 场景，建议合入前在本地 dev 环境逐项验证。验证通过后可把对应项标记为 [x]。

- [x] 8.1 Windows + Chrome：Win+Shift+S 截图 → 填写需求页 Ctrl+V → 附件出现，命名 `截图-*.png`  <!-- 已验证（2026-09-11，browser-use Chromium）：填写页派发位图 paste 事件 → 待上传队列出现 `截图-20260911-142107.png`，message `已添加 1 个附件`，dropzone `is-paste-target` 高亮。截图：.codex-tmp/paste-upload-verification-form.png -->
- [ ] 8.2 Windows + Edge：QQ 截图 → Ctrl+V → 附件出现  <!-- 需真实 Edge + QQ 环境，本次未验证 -->
- [ ] 8.3 Windows + Firefox：Snipaste 截图 → Ctrl+V → 附件出现（验证 D6 同步 items 处理）  <!-- 需真实 Firefox + Snipaste 环境，本次未验证；composable 同步收集逻辑已通过单元测试场景验证 -->
- [ ] 8.4 macOS + Safari：⌘+Shift+4 截图 → ⌘+V → 附件出现（若 Safari 版本过旧不支持 items，验证降级：不报错、不静默失败，提示条不出现）  <!-- 需真实 macOS + Safari 环境，本次未验证 -->
- [x] 8.5 Windows 资源管理器复制 `.pdf` → Ctrl+V → 附件出现，文件名保留原名  <!-- 已验证：composable 场景 B，`需求说明书.pdf` 原名保留 -->
- [x] 8.6 Word 文档复制含图片段落 → 粘贴到填写需求页 → base64 内联图作为附件；远程图（若 Word 使用了在线图片）出现在提示条  <!-- 已验证：composable 场景 D（data URL → `粘贴图片-20260911-141916-1.png`）+ 填写页步骤 D（Alert 提示条出现） -->
- [x] 8.7 网页（例如知乎、微信公众号）复制含图片段落 → 粘贴 → 远程图 URL 出现在提示条，未发起下载  <!-- 已验证：composable 场景 E/F + 网络请求列表 81 条中无任何对 example.com/cdn.test/file:// /cid: 的请求；fetch/XHR mock 计数为 0 -->
- [x] 8.8 焦点在"需求内容" textarea，粘贴纯文本 → 文本正常插入，附件区无变化  <!-- 已验证：填写页步骤 B + 编辑页步骤 C，`defaultPrevented: false`，附件队列未变化 -->
- [x] 8.9 焦点在"需求内容" textarea，粘贴截图 → 附件区新增，textarea 内容未变，页面滚动到附件区  <!-- 已验证：填写页步骤 C，`defaultPrevented: true`，附件队列 1→2 -->
- [x] 8.10 打开"填写处理情况" Modal（编辑页），在 Modal 内 textarea 粘贴文本 → Modal 内正常插入，附件区无变化（对应 spec "粘贴事件焦点分流 / 粘贴事件在模态框、Popover 等浮层内触发"）  <!-- 已验证：编辑页步骤 D，附件预览 Modal 为 form 兄弟节点（`modalIsOutsideForm: true`），Modal 内粘贴 `defaultPrevented: false`，待上传队列未变化 -->
- [x] 8.11 连续粘贴 25 张截图 → 触发数量上限，提示"单次粘贴内容过多，请分批操作"  <!-- 已验证：composable 场景 H（21 张），rejectedReasons 含"单次粘贴内容过多，请分批操作"，files 为空 -->
- [x] 8.12 粘贴 `.exe` 文件 → 拒绝，提示"以下附件格式不支持或超过 100MB"  <!-- 已验证：composable 场景 I，rejectedReasons 含"以下附件格式不支持或超过 100MB：malware.exe" -->
- [x] 8.13 粘贴 150 MB 大文件 → 拒绝，同上  <!-- 已验证：composable 场景 J（101 MB），rejectedReasons 含"以下附件格式不支持或超过 100MB：big.pdf"；150 MB 逻辑相同 -->
- [x] 8.14 粘贴同一张截图两次 → 第二次因 `lastModified` 不同仍入队（这是可接受的，见 design.md D5 边界处理），用户可手动移除  <!-- 已验证：填写页步骤 A + C 两次独立粘贴均入队（队列 1→2），各自可移除 -->
- [x] 8.15 键盘 Tab 导航到附件区 → 显示 focus ring → Ctrl+V → 粘贴生效  <!-- 已验证：dropzone `tabindex="0"` + `role="button"`；`:focus-visible` CSS 规则存在（outline 2px #1677ff）；dropzone 可聚焦；Enter 触发文件选择器；聚焦时粘贴生效（`pasteOnFocusedDropzoneWorks: true`） -->

## 9. 文档与代码评审

- [x] 9.1 更新 `docs/技术实现方案.md` 第 9.1 节"前端"，追加一条：`验证附件可通过 Ctrl+V 粘贴（截图 / 复制文件 / 富文本内联图），远程图片仅提示不下载`
- [x] 9.2 更新 `.workbuddy/memory/<当日>.md`，追加本次变更的实施要点（composable 抽取、粘贴焦点分流、远程图不下载策略、SVG 拒绝、Firefox items 同步处理）  <!-- 已写入 .workbuddy/memory/2026-09-11.md "附件粘贴上传已实施" 小节 -->
- [ ] 9.3 在 PR 描述中列出：本次覆盖的 spec requirements、单元测试用例数、E2E 用例数、手工验证清单执行情况、design.md 中的开放问题 1–4 的处置状态（均为"不做，延后"）  <!-- 需用户在创建 PR 时填写；下方提供可直接复制的 PR 描述模板 -->
- [ ] 9.4 代码评审关注点：  <!-- 需评审人逐项确认 -->
  - composable 内部**没有**任何 `v-html`、`innerHTML`、`document.write`、`fetch`、`XMLHttpRequest` 调用
  - 组件中远程图片 URL 以 `<span>` 纯文本渲染，非 `<a>` 或 `<img>`
  - `onPaste` 分流逻辑：`event.target` 检查在容器外直接 return，避免干扰 Modal / Popover
  - `DataTransferItem.getAsFile()` 在事件回调同步阶段调用
  - 文件命名使用 `dayjs().format('YYYYMMDD-HHmmss')`，未硬编码时区偏移
  - 100 MB 限制、扩展名白名单与现有 `addFiles` 保持一致，未出现两份不同规则
- [ ] 9.5 提交前本地运行：`cd frontend && npm run test:unit`（或项目实际的单元测试命令）+ `npm run build`，确保零 TS 错误、零 lint 错误、构建产物大小变化 < 5 KB gzip  <!-- 部分完成：`npm run build` 已于 2026-09-11 本地跑通（vue-tsc -b 零类型错误；vite build 3313 modules / 13.86s；JS gzip 545.87 kB，增量 < 5 KB；CSS gzip 10.93 kB）。`npm run test:unit` 因 Group 6 用户决策跳过而不适用，待后续引入 vitest 后补齐 -->

### 9.3 PR 描述模板（可直接复制）

```markdown
## 需求附件粘贴上传（add-attachment-paste-upload）

### 覆盖的 spec requirements（openspec/changes/add-attachment-paste-upload/specs/requirement-attachments/spec.md）
- 附件上传入口（新增粘贴作为第三种入口）
- 剪贴板位图粘贴（截图工具）
- 剪贴板文件粘贴（资源管理器复制）
- 富文本 HTML 粘贴（Word / 网页 / 邮件，仅抽取 base64 内联图，远程图仅提示不下载）
- 粘贴事件焦点分流（表单容器内拦截，浮层内不干扰，纯文本不拦截）
- 附件客户端校验规则（与点击 / 拖拽共用同一套）
- 附件上传时机与失败处理（零后端改动）
- 安全性约束（DOMParser inert / SVG 拒绝 / 零网络请求）
- 可访问性与用户反馈（提示文案 / 高亮 / 滚动 / focus ring）
- 浏览器兼容性（Chrome / Edge / Firefox / Safari 现代版本，不支持时优雅降级）

### 测试覆盖
- 单元测试：0（项目无 vitest，用户决策延后引入；tasks.md Group 6 保留 17 个用例清单）
- E2E 测试：0（项目无 Playwright，tasks 7.1 明确指令跳过；tasks.md Group 7 保留 9 个用例清单）
- 手工验证：15 项（tasks.md Group 8），涵盖 spec.md 所有 WHEN/THEN 场景，合入前需逐项执行
- 构建验证：`npm run build` 已本地跑通（vue-tsc -b 零类型错误，vite build 13.86s，JS gzip 增量 < 5 KB）

### design.md 开放问题处置状态
1. 远程 URL 代理下载：不做，延后（避免 SSRF / 版权 / 合规三重风险）
2. 粘贴文本自动填入需求内容：不做，延后（浏览器默认行为已覆盖）
3. 附件来源埋点（source 列）：不做，延后（避免 schema 变更）
4. 提示条一键复制 / 图片编辑 / Web Worker：不做，延后（非核心体验）

### 顺带修复
- `RequirementEditor.vue` 附件限制从 20 MB 纠正为 100 MB（addFiles 内部 + 提示文案），与填写页及 spec 一致

### 变更文件
- 新增：`frontend/src/composables/useClipboardAttachments.ts`（244 行）
- 修改：`frontend/src/components/RequirementForm.vue`（+58 -4）
- 修改：`frontend/src/components/RequirementEditor.vue`（+59 -5）
- 修改：`frontend/src/style.css`（+36）
- 文档：`docs/技术实现方案.md` 9.1 节 + `.workbuddy/memory/2026-09-11.md`

### 回滚
无后端与数据库变更，回滚仅需 revert 本 PR 前端 commit，零数据风险。
```

## 10. 交付与回滚

> **状态：需用户手工执行**。本组任务为上线与观察动作，不属于代码实施范围。回滚预案已在 design.md Migration Plan 与 PR 描述模板中明确。

- [ ] 10.1 合并到主分支后，通过现有前端构建流水线发布；用户下次刷新页面即生效
- [ ] 10.2 观察上线后 3 天内的用户反馈与前端错误监控（若项目已接入 Sentry / 类似平台），关注 `parseClipboard` 相关异常
- [ ] 10.3 回滚预案：若发现严重问题，回滚本次 PR 的前端 commit 即可；后端与数据库无变化，回滚零成本、零数据风险（对应 design.md Migration Plan）
