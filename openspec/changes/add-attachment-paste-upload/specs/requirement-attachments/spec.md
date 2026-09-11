## Purpose

定义需求（Requirement）附件的完整行为契约：上传入口（点击 / 拖拽 / 粘贴）、客户端与服务端校验、待上传队列管理、上传时机、可访问性反馈与安全边界。本 capability 覆盖填写需求页与编辑需求页两个使用场景。

## ADDED Requirements

### Requirement: 附件上传入口

系统 SHALL 在填写需求页（`RequirementForm`）与编辑需求页（`RequirementEditor`）提供三种互不排斥的附件上传入口：**点击选择文件**、**拖拽文件到附件区**、**从剪贴板粘贴**。三种入口产生的文件 SHALL 汇入同一个"待上传队列"，共用同一套客户端校验规则，并通过同一个后端接口 `POST /api/requirements/{id}/attachments` 完成上传。

#### Scenario: 用户点击附件区选择文件

- **WHEN** 用户点击附件区（dropzone）或按下 Enter / Space 键（dropzone 具有 `tabindex=0` 且 `role=button`）
- **THEN** 系统打开操作系统文件选择器，多选允许
- **AND** 用户确认选择后，通过校验的文件加入待上传队列，未通过校验的文件被拒绝并显示原因

#### Scenario: 用户拖拽文件到附件区

- **WHEN** 用户将一个或多个文件从操作系统文件管理器拖入附件区并释放
- **THEN** 拖拽期间附件区显示"可放置"视觉状态（`is-dragging` CSS class）
- **AND** 释放后，通过校验的文件加入待上传队列，未通过校验的文件被拒绝并显示原因

#### Scenario: 用户从剪贴板粘贴

- **WHEN** 用户在表单容器范围内按下 Ctrl+V（Windows / Linux）或 ⌘+V（macOS）或 Shift+Insert 或选择浏览器"粘贴"菜单
- **AND** 剪贴板中包含位图、文件或富文本 HTML
- **THEN** 系统按"剪贴板位图粘贴"、"剪贴板文件粘贴"、"富文本 HTML 粘贴"三项 requirement 中定义的规则解析并加入待上传队列
- **AND** 附件区显示"已聚焦为粘贴目标"视觉状态（`is-paste-target` CSS class）持续 1.2 秒

### Requirement: 剪贴板位图粘贴

当剪贴板中存在 `kind === 'file'` 且 `type` 以 `image/` 开头的 `DataTransferItem` 时，系统 SHALL 将其转换为 `File` 对象加入待上传队列，并 SHALL 为该文件生成语义化文件名。

#### Scenario: 粘贴截图工具产生的位图

- **WHEN** 用户使用系统截图工具（如 Windows Snipping Tool、Win+Shift+S、macOS ⌘+Shift+4、Snipaste、QQ / 微信截图）截图后，在表单容器内按下粘贴快捷键
- **THEN** 系统从 `ClipboardEvent.clipboardData.items` 中提取 `image/png`（或 `image/jpeg`、`image/webp`）blob
- **AND** 生成文件名格式为 `截图-YYYYMMDD-HHmmss.<ext>`（时区为 Asia/Shanghai，`ext` 根据 MIME 类型映射：`image/png → png`、`image/jpeg → jpg`、`image/webp → webp`、`image/gif → gif`）
- **AND** 文件加入待上传队列，用户看到 message 提示"已添加 1 个附件"

#### Scenario: 同一次粘贴包含多张位图

- **WHEN** 剪贴板中同时包含多个 `image/*` 的 `DataTransferItem`（例如从多图聊天工具复制）
- **THEN** 系统 SHALL 全部提取，按 `截图-YYYYMMDD-HHmmss-<index>.<ext>` 命名（`index` 从 1 递增）
- **AND** message 提示"已添加 N 个附件"

#### Scenario: 位图格式不在白名单

- **WHEN** 剪贴板中的图片 MIME 类型不在白名单（例如 `image/tiff`、`image/bmp`、`image/svg+xml`）
- **THEN** 系统 SHALL 拒绝该文件，不加入队列
- **AND** message 提示"以下附件格式不支持：<文件名或 MIME 类型>"
- **AND** SVG 一律拒绝，因为 SVG 可携带脚本，存在 XSS 风险

#### Scenario: 位图与文件同时存在于剪贴板

- **WHEN** 剪贴板同时包含 `image/*` blob 与 `clipboardData.files`（非空）
- **THEN** 系统 SHALL 优先使用 `clipboardData.files`（视为用户显式复制的文件），忽略 blob，避免重复入队

### Requirement: 剪贴板文件粘贴

当 `ClipboardEvent.clipboardData.files` 非空时，系统 SHALL 将其视为与拖拽完全等价的文件列表，走同一 `addFiles` 校验管线。

#### Scenario: 从资源管理器复制文件后粘贴

- **WHEN** 用户在 Windows 资源管理器 / macOS Finder 中 Ctrl+C 复制一个或多个文件，然后在本表单容器内 Ctrl+V
- **AND** `clipboardData.files` 非空
- **THEN** 系统将 `clipboardData.files` 中每个 `File` 送入 `addFiles`，与拖拽路径共用同一套扩展名白名单校验、单文件大小校验（≤ 100 MB）、重复检测
- **AND** 通过校验的文件加入待上传队列，未通过的按现有拒绝逻辑提示

#### Scenario: 复制的文件扩展名不在白名单

- **WHEN** 用户粘贴一个 `.exe`、`.zip`、`.txt` 或其他非白名单扩展名的文件
- **THEN** 系统拒绝该文件，不加入队列
- **AND** message 提示"以下附件格式不支持或超过 100MB：<文件名>"

#### Scenario: 复制的文件超过 100 MB

- **WHEN** 用户粘贴单个大于 100 MB 的文件
- **THEN** 系统拒绝该文件，不加入队列
- **AND** message 提示与上一场景相同的拒绝文案

#### Scenario: 复制的文件与队列中已有文件重复

- **WHEN** 用户粘贴的文件与待上传队列中某个文件在 `name`、`size`、`lastModified` 三个字段上完全一致
- **THEN** 系统静默丢弃重复项，不加入队列
- **AND** 不显示错误 message（与拖拽 / 点击去重行为一致）

### Requirement: 富文本 HTML 粘贴

当剪贴板包含 `text/html` 数据且其中含 `<img>` 标签时，系统 SHALL 使用 `DOMParser` 在**独立文档**中解析该 HTML，抽取内联 base64 图片作为附件，并 SHALL 对远程图片 URL 采取"仅提示、不下载"的策略。

#### Scenario: 从 Word / 网页 / 邮件复制含内联 base64 图片的内容

- **WHEN** 用户复制的内容包含 `<img src="data:image/png;base64,iVBORw0K...">` 或 `data:image/jpeg;base64,...` 等 data URL
- **THEN** 系统 SHALL 解码 base64 → `Blob` → `File`，文件名格式为 `粘贴图片-YYYYMMDD-HHmmss-<index>.<ext>`（`index` 从 1 递增，`ext` 根据 data URL 的 MIME 部分映射）
- **AND** 通过白名单校验后加入待上传队列

#### Scenario: 从网页复制含远程 http(s) 图片的内容

- **WHEN** 用户复制的 HTML 包含 `<img src="https://example.com/foo.png">` 或其他 `http://` / `https://` URL
- **THEN** 系统 SHALL **不发起任何网络请求**、**不下载**这些图片
- **AND** 系统 SHALL 在附件区上方显示一条可关闭的提示条：`检测到 N 张远程图片，出于安全与版权考虑不会自动下载。如需作为附件，请右键图片"另存为"后再上传。`
- **AND** 提示条 SHALL 提供"查看链接"按钮，展开后以纯文本列表显示所有远程图片 URL（不渲染为 `<img>`、不使用 `v-html`），每项可复制到剪贴板
- **AND** 提示条 SHALL 在下一次粘贴事件、页面卸载或用户点击关闭按钮时消失

#### Scenario: HTML 中的 file:// 或 cid: 图片引用

- **WHEN** HTML 包含 `<img src="file:///C:/...">`（本地文件路径）或 `<img src="cid:...">`（邮件内嵌引用）
- **THEN** 由于浏览器安全策略无法访问，系统 SHALL 将其归类为"不可提取的远程图片"，纳入上一场景的提示条计数与 URL 列表
- **AND** URL 列表中 SHALL 明确标注"（浏览器安全策略不允许访问，请手动附加原文件）"

#### Scenario: HTML 解析异常

- **WHEN** `DOMParser.parseFromString(html, 'text/html')` 抛错或返回包含 `<parsererror>` 的文档
- **THEN** 系统 SHALL 静默跳过 HTML 解析路径，不影响 `clipboardData.items` / `clipboardData.files` 路径的处理
- **AND** 不向用户展示技术性错误 message（避免噪音）

#### Scenario: HTML 中的 SVG 图片或 `<script>`、`<iframe>`、`<object>` 标签

- **WHEN** HTML 包含 `<img src="data:image/svg+xml;...">` 或任何 `<script>`、`<iframe>`、`<object>`、`<embed>` 标签
- **THEN** 系统 SHALL 忽略这些内容，不加入待上传队列，不在提示条中显示
- **AND** 解析过程 SHALL 在独立 `Document` 中完成，**不将任何片段插入当前页面 DOM**，杜绝 XSS

### Requirement: 粘贴事件焦点分流

系统 SHALL 在**整个表单容器**（而非仅附件区）监听 `paste` 事件；SHALL 根据当前焦点位置和剪贴板内容决定是否拦截默认行为。

#### Scenario: 焦点在文本输入控件且剪贴板只有纯文本

- **WHEN** 用户当前焦点在"需求标题"、"需求内容"、"提出人姓名"等 `<input>` / `<textarea>` 控件内
- **AND** 剪贴板只包含 `text/plain`（无 `image/*`、无 `Files`、无 `text/html` 中的 `<img>`）
- **THEN** 系统 SHALL **不拦截**默认粘贴行为，浏览器把文本插入到当前光标位置

#### Scenario: 焦点在文本输入控件但剪贴板含图片或文件

- **WHEN** 用户当前焦点在文本输入控件内
- **AND** 剪贴板包含 `image/*` blob 或 `clipboardData.files` 非空或 `text/html` 中含 `<img>`
- **THEN** 系统 SHALL 调用 `event.preventDefault()` 阻止默认行为
- **AND** 按前述 requirement 解析剪贴板并加入附件待上传队列
- **AND** 附件区 SHALL 短暂高亮（`is-paste-target` class 持续 1.2 秒）+ 页面滚动到附件区（`scrollIntoView({ behavior: 'smooth', block: 'center' })`），提示用户"内容已作为附件添加"

#### Scenario: 焦点不在任何输入控件（例如刚点击了空白区域）

- **WHEN** 焦点位于 `<body>` 或表单容器内的非输入元素
- **AND** 剪贴板包含图片、文件或富文本
- **THEN** 系统 SHALL 拦截并按附件路径处理

#### Scenario: 焦点在附件区 dropzone 上

- **WHEN** 用户已通过 Tab 键或点击将焦点定位到附件区 dropzone
- **AND** 剪贴板包含任意可解析内容
- **THEN** 系统 SHALL 拦截并按附件路径处理（与上一场景一致）

#### Scenario: 粘贴事件在模态框、Popover 等浮层内触发

- **WHEN** 当前打开的 Ant Design `Modal`、`Popover`、`Select` 下拉等浮层内的输入控件获得焦点
- **AND** 用户在浮层内粘贴
- **THEN** 系统 SHALL **不拦截**，避免干扰浮层内正常的文本粘贴行为（通过检查 `event.target` 是否位于表单容器内实现）

### Requirement: 附件客户端校验规则

系统 SHALL 对所有入口（点击 / 拖拽 / 粘贴）产生的文件应用**同一套**客户端校验规则，且 SHALL 在文件加入待上传队列**之前**完成校验。

#### Scenario: 扩展名白名单

- **WHEN** 文件的扩展名（小写化后）不属于 `{jpg, jpeg, png, gif, webp, pdf, doc, docx, xls, xlsx}`
- **THEN** 系统拒绝该文件

#### Scenario: 单文件大小限制

- **WHEN** 文件字节数 > 100 × 1024 × 1024
- **THEN** 系统拒绝该文件

#### Scenario: 重复文件检测

- **WHEN** 待加入的文件与队列中已有文件在 `name`、`size`、`lastModified` 三个字段上完全一致
- **THEN** 系统静默丢弃重复项，不显示错误

#### Scenario: 拒绝原因聚合提示

- **WHEN** 一次粘贴 / 拖拽 / 选择操作中同时有多个文件被拒绝
- **THEN** 系统 SHALL 聚合为一条 message 提示（`message.warning`），列出所有被拒绝的文件名，用中文顿号分隔，而非为每个文件单独弹一条

### Requirement: 附件上传时机与失败处理

系统 SHALL 保持现有的上传时机语义：填写需求页在需求主体保存成功后统一上传附件；编辑需求页在用户显式点击"上传附件"按钮后触发。粘贴入口 SHALL 不改变这一时机。

#### Scenario: 填写需求页保存后上传

- **WHEN** 用户在填写需求页完成必填项并点击"保存需求" / "暂存"
- **AND** 待上传队列非空（可能包含粘贴、拖拽、点击来源的文件）
- **THEN** 系统 SHALL 先创建需求（`POST /api/requirements` 或 `POST /api/requirements/drafts`），成功后逐个上传附件（`POST /api/requirements/{id}/attachments`）
- **AND** 上传进度以现有 `uploadProgress` 显示

#### Scenario: 编辑需求页显式上传

- **WHEN** 用户在编辑需求页向待上传队列加入文件（含粘贴）
- **THEN** "上传附件"按钮出现（`v-if="selectedFiles.length"`）
- **AND** 用户点击后逐个上传，成功后刷新附件列表并触发预览生成轮询

#### Scenario: 部分附件上传失败

- **WHEN** 保存需求成功但附件上传过程中某个文件失败（例如后端魔数校验拒绝）
- **THEN** 系统 SHALL 保留已成功的附件，显示后端返回的具体 message（复用 `apiErrorDetails`），并提示"需求已保存，但部分附件上传失败，可稍后在详情页重试"
- **AND** 不阻塞路由跳转（填写页仍跳转到需求列表）

### Requirement: 安全性约束

系统 SHALL 保证粘贴上传路径不引入 XSS、SSRF、路径穿越等安全风险。

#### Scenario: HTML 解析隔离

- **WHEN** 系统解析剪贴板中的 `text/html` 数据
- **THEN** 解析 SHALL 通过 `new DOMParser().parseFromString(html, 'text/html')` 在独立文档中完成
- **AND** 解析结果 SHALL **不**被插入到当前页面 DOM，**不**通过 `v-html` 渲染，**不**执行其中的 `<script>`

#### Scenario: 远程 URL 不发起请求

- **WHEN** 剪贴板 HTML 中包含 `http://` / `https://` / `file://` / `cid:` / `ftp://` 等任何非 `data:` 协议的 `<img src>`
- **THEN** 系统 SHALL 不发起任何网络请求或本地文件读取
- **AND** 提示条中的 URL 列表 SHALL 以纯文本形式呈现（`<span>{{ url }}</span>`），不渲染为可点击的 `<a href>` 或 `<img src>`，避免浏览器自动预取或触发 Referer 泄露

#### Scenario: SVG 一律拒绝

- **WHEN** 剪贴板中出现 `image/svg+xml` MIME 类型（无论作为 blob、`File`、还是 data URL）
- **THEN** 系统 SHALL 拒绝该文件，因为 SVG 可包含 `<script>` 与外部实体引用，存在存储型 XSS 风险
- **AND** 拒绝原因纳入聚合提示 message

#### Scenario: 后端魔数校验作为最终防线

- **WHEN** 客户端校验通过但文件实际内容与扩展名不符（例如伪造的 `screenshot.png` 实际是文本）
- **THEN** 后端 `AttachmentService.detectActualFormat` SHALL 拒绝该文件并返回 `无法识别附件内容格式，请确认文件完整或转换为受支持格式（PDF / Word / 图片等）`
- **AND** 前端 SHALL 通过 `apiErrorDetails` 显示该 message（与拖拽 / 点击路径一致）

### Requirement: 可访问性与用户反馈

系统 SHALL 为粘贴上传提供清晰的可发现性、焦点管理与状态反馈。

#### Scenario: 附件区提示文案

- **WHEN** 用户查看填写需求页或编辑需求页的附件区
- **THEN** 提示文案 SHALL 明确包含"粘贴"入口，例如：`支持图片、PDF、Word、Excel，单个文件最大 100MB。可点击选择、拖入，或直接 Ctrl+V 粘贴截图与文件。`

#### Scenario: 粘贴成功反馈

- **WHEN** 粘贴操作成功添加 N 个文件到待上传队列（N ≥ 1）
- **THEN** 系统 SHALL 显示 `message.success('已添加 N 个附件')`（N === 1 时为"已添加 1 个附件"）
- **AND** 附件区 SHALL 短暂高亮 1.2 秒（视觉引导用户注意）
- **AND** 若焦点原本不在附件区，SHALL 平滑滚动到附件区

#### Scenario: 粘贴全部被拒绝

- **WHEN** 粘贴的所有文件均因格式、大小或重复被拒绝
- **THEN** 系统 SHALL 显示聚合的 `message.warning`（沿用现有拒绝文案）
- **AND** 附件区高亮 SHALL 使用"警告色"（区别于成功时的"聚焦色"）

#### Scenario: 剪贴板为空或仅含系统不支持的数据

- **WHEN** 剪贴板为空、仅含 `text/plain`、或仅含系统无法识别的 MIME 类型（例如 `application/x-custom`）
- **THEN** 系统 SHALL 不拦截默认行为，不显示任何 message（避免噪音）

#### Scenario: 键盘可达性

- **WHEN** 用户使用键盘 Tab 键导航到附件区 dropzone
- **THEN** dropzone SHALL 具有 `tabindex="0"` 与 `role="button"`，获得焦点后按 Ctrl+V SHALL 触发粘贴解析
- **AND** dropzone 获得焦点时 SHALL 显示可见的 focus ring（沿用 Ant Design 默认样式或自定义 `.attachment-dropzone:focus-visible`）

### Requirement: 浏览器兼容性

系统 SHALL 在现代浏览器（Chrome / Edge / Firefox / Safari 最近两个大版本）中提供完整的粘贴上传能力；SHALL 在不支持剪贴板 API 的环境中优雅降级。

#### Scenario: ClipboardEvent.clipboardData 不可用

- **WHEN** 浏览器不支持 `ClipboardEvent.clipboardData`（例如 IE 或极旧版本 Safari）
- **THEN** 系统 SHALL 不注册 `paste` 事件监听器（或在事件回调中直接 return）
- **AND** 点击选择、拖拽上传路径 SHALL 完全不受影响
- **AND** 不显示任何错误 message

#### Scenario: DataTransferItem.getAsFile 返回 null

- **WHEN** 某些浏览器（例如 Firefox 部分版本）在 `paste` 事件后异步访问 `items[i].getAsFile()` 返回 `null`
- **THEN** 系统 SHALL 在事件回调内**同步**遍历 items 并立即调用 `getAsFile()`，避免异步丢失
- **AND** 若返回 `null`，SHALL 跳过该项并继续处理其他项，不抛异常
