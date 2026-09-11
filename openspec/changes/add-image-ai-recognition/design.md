## Context

见 `proposal.md - Why`。影响技术选型的现状约束：

- 后端 `AiAnalysisService.callModel` 当前以 OpenAI 兼容格式发送 `messages: [{role: "user", content: "<string>"}]`；多模态需把 `content` 改为数组 `[{type:"text",text:...},{type:"image_url",image_url:{url:"data:<mime>;base64,<...>"}}]`。
- `ai_config` 多配置单激活（`findByIsActiveTrue`）；当前激活 `mimo-v2.5` @ `api.xiaomimomo.com`，**是否支持视觉未验证**，故错误引导是必需路径而非边缘情况。
- `buildPrompt` 已注入部门 / 类型 / 系统版本字典；`parseAndValidate` 已做字典与日期校验——图片路径必须复用二者，避免两套提取规则漂移。
- `spring.servlet.multipart.max-file-size: -1`（全局不限制），图片大小限制需在应用层显式实现。
- 前端 `RequirementForm.vue` 的 `analyzeWithAi` 已实现"逐字段填空 + 计数 + message"，且刚完成的粘贴上传变更提供了剪贴板 items 解析的成熟模式（同步取 blob）。

## Goals / Non-Goals

**Goals:**
- 一次 HTTP 调用完成"截图 → 9 字段结构化"，复用现有配置、prompt 字典、校验管线。
- 图片全生命周期限于单次请求内存：读取 → base64 → 发送 → 丢弃。
- 模型不支持视觉时给出可操作引导，而不是让用户面对 502 猜原因。
- 文本导入与图片识别共用同一份"只填空字段"填充实现，规则永不漂移。

**Non-Goals:**
- **不做**预览确认弹窗（用户决策：直接填空字段）。
- **不做**多图合并识别（单张）。
- **不做**附件区图片"AI 识别"联动按钮（用户决策）。
- **不做**OCR 中间文本可编辑的两步式交互（方案 B，已否决）。
- **不做**第三方 OCR 厂商集成（方案 C，已否决）。
- **不做**图片持久化 / 识别历史 / 审计留痕。
- **不做**AI 配置页的"模型视觉能力探测"按钮（错误引导已足够，探测按钮留作后续）。

## Decisions

### D1：接口形态 = multipart 单文件字段 `file`

**决定**：`POST /api/ai/analyze-image`，`@RequestParam("file") MultipartFile file`，与附件上传接口形态一致。

**Alternatives considered**：
- **A：JSON base64 体**——前端 `FileReader` 转 dataURL 简单，但 10 MB 图片 base64 后约 13.3 MB JSON 字符串，Tomcat 需整体读入内存且无法流式；multipart 由 Spring 解析落临时文件再流式读取，内存更可控。**拒绝**。
- **B：multipart（选中）**——与项目现有附件上传一致，前端 `FormData` 一行搞定。**采纳**。

### D2：视觉消息构建 = content 数组（text + image_url data URL）

**决定**：服务端把图片字节转 base64，拼 `data:<contentType>;base64,<...>` 作为 `image_url.url`；`content` 为两部分数组。contentType 取魔数识别结果（不信任客户端 MIME）。

**理由**：OpenAI 兼容协议的事实标准；data URL 避免服务端再托管临时图片 URL（与"不落地"一致）。

**Alternatives considered**：
- **A：先把图片上传到对象存储再传 URL**——引入存储依赖，违背不落地。**拒绝**。
- **B：data URL（选中）**——零额外依赖。**采纳**。

### D3：prompt 复用 = 抽出 `buildExtractionPrompt(dictContext, userContentInstruction)`

**决定**：把现有 `buildPrompt` 拆为两层：字典上下文构建（部门 / 类型 / 系统版本 + JSON schema + 规则 1–7）与用户输入段。文本路径传入"用户文本：X"；图片路径传入"用户截图说明 + 噪音过滤指令"，并以视觉消息携带图片。两条路径共享同一 schema 与规则，`parseAndValidate` 完全复用。

图片路径额外指令要点：
- 截图可能是微信 / QQ 聊天、邮件、文档截图；
- 忽略昵称、头像、时间戳、表情、"已读/撤回"等系统元素；
- requesterName 仅在聊天上下文**明确**表明某人提出需求时填写，否则 null；
- 图片中的文字若与需求无关（广告、闲聊）不提取。

**Alternatives considered**：
- **A：为图片单独写一套完整 prompt**——schema 与字典规则重复维护，必然漂移。**拒绝**。
- **B：分层复用（选中）**。**采纳**。

### D4：输入校验 = 扩展名 + 魔数双重，10 MB 上限

**决定**：服务端校验顺序：`file.isEmpty()` → 大小 ≤ 10 MB → 魔数识别（png `89 50 4E 47`、jpg `FF D8 FF`、webp `RIFF....WEBP`）→ 不通过返回 400 + 明确文案。扩展名仅作前端预校验与提示用，服务端以魔数为准（与附件服务"内容优先"哲学一致）。

**Alternatives considered**：
- **A：只信 Content-Type / 扩展名**——可伪造。**拒绝**。
- **B：魔数为准（选中）**。**采纳**。

### D5：视觉错误映射 = 状态码 + 错误体关键词

**决定**：`callModel` 增加响应体捕获：非 2xx 时读取 body；若 status ∈ [400, 500) 且 body（小写）含 `image` / `vision` / `visual` / `multimodal` / `图片` / `视觉` 任一关键词 → 抛引导性错误"当前激活模型可能不支持图片识别，请在 AI 配置中切换为视觉模型（如 qwen-vl、glm-4v、mimo-vl 等）后重试"；否则沿用"AI 服务返回错误，状态码：N"。文本路径不启用该映射（保持原行为）。

**Alternatives considered**：
- **A：配置页加"视觉能力探测"按钮**——额外 UI + 探测请求本身也可能被厂商拒绝，收益有限。**拒绝（留作开放问题）**。
- **B：错误体关键词映射（选中）**——零配置、覆盖主流厂商报错。**采纳**。

### D6：独立超时 = `app.ai.image-request-timeout-seconds: 60`

**决定**：`AiAnalysisService` 注入第二个 `@Value`；`analyzeImage` 用该超时构建 HttpClient 与 request timeout；文本路径不变。

**理由**：视觉推理普遍比纯文本慢 2–4 倍；30 s 会对大图 / 慢模型误杀。

### D7：前端入口 = AI 面板内上传按钮 + 面板级 paste 监听

**决定**：
- AI 折叠面板内新增一行：`<Button :loading="aiImageAnalyzing">识别需求截图</Button>` + 隐藏 `<input type="file" accept="image/png,image/jpeg,image/webp">`。
- 面板容器 `<CollapsePanel>` 内容区挂 `@paste`：同步遍历 `clipboardData.items` 取第一个 `kind==='file' && type.startsWith('image/')` 的 blob（复用粘贴上传变更的同步取 blob 模式）；**仅当取到图片 blob 时** `preventDefault`，否则不影响 AI 文本框的普通文本粘贴。
- 客户端预校验：`type` 白名单 + `size ≤ 10 MB`，不通过 message.warning 且不发起请求。
- 与附件区粘贴监听的关系：AI 面板在附件区**之外**（折叠面板位于表单网格上方），两个 paste 监听各自挂在各自容器上，事件冒泡不会交叉触发（AI 面板内粘贴图片时，事件 target 不在附件区容器内；附件区容器监听挂在 `.requirement-form-shell`……

  **注意**：填写页附件粘贴监听挂在根 `<section>`，AI 面板在其内部！因此 AI 面板内粘贴图片会**同时**命中两个监听。解决：AI 面板 paste 回调中取到图片 blob 并处理后调用 `event.stopPropagation()`，阻止冒泡到根 section 监听；根 section 监听在 AI 面板场景下不会重复入队。反向（附件区粘贴）不受影响，因为附件 dropzone 在 AI 面板外，事件不经过 AI 面板监听。

**Alternatives considered**：
- **A：只做上传按钮不做粘贴**——用户截图后习惯 Ctrl+V，少一个入口少一分便利。**拒绝**。
- **B：上传 + 面板粘贴 + stopPropagation（选中）**。**采纳**。

### D8：填充逻辑复用 = 抽 `applyAiAnalysis(data): number`

**决定**：把 `analyzeWithAi` 内"逐字段 if 空则填 + filled++"抽为组件内函数 `applyAiAnalysis`；文本导入与图片识别都调用它；返回值 = 填充字段数；`filled === 0` 时文本路径提示"AI 未能从文本中识别出有效字段"、图片路径提示"AI 未能从图片中识别出有效字段"。

**理由**：单一实现保证两条路径填充规则一致（spec "已有内容不被覆盖"）。

### D9：测试与验证策略

- **后端**：项目无 mvn，沿用 `.workbuddy/memory/2026-09-11.md` 记录的手动编译 + junit-platform-launcher 方式，为魔数校验与视觉错误映射写单元测试（mock HttpClient 较难，故错误映射以纯函数 `mapVisionError(status, body)` 形式抽出便于单测）。
- **前端**：无 vitest（用户决策延后引入），不写单测。
- **端到端**：用 browser-use 在真实浏览器验证：AI 面板上传按钮出现、粘贴图片触发请求（Network 面板可见 `/api/ai/analyze-image`）、错误引导文案在 mimo-v2.5 不支持视觉时正确显示、文本导入不受影响。若 mimo-v2.5 实际支持视觉，则进一步验证字段填充。
- **回归**：现有文本 AI 导入、附件粘贴上传行为不变。

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|------|--------|-----------|
| 激活模型 mimo-v2.5 不支持视觉 | 功能不可用，用户困惑 | D5 引导性错误 + 提示换模型；AI 配置支持多配置，用户可另建视觉配置并激活 |
| 厂商视觉报错文案不含关键词 | 引导不触发，退化为通用错误 | 关键词表覆盖中英文常见词；通用错误仍含状态码可排查；后续可按厂商补充 |
| 10 MB base64 请求体（约 13.3 MB） | 内存与带宽压力 | multipart 流式读取 + 仅在构建请求体时持有一份 base64；请求结束即释放；上限硬编码 10 MB |
| 微信截图噪音导致误填（昵称→提出人） | 表单数据错误 | prompt 显式噪音过滤指令 + requesterName 强约束；只填空字段，用户可改 |
| AI 面板 paste 与附件区 paste 监听交叉 | 图片同时进 AI 识别与附件队列 | D7 stopPropagation 阻断冒泡；两监听容器父子关系明确 |
| 视觉调用 30–60 s 用户等待 | 体验差 | loading 状态 + 独立 60 s 超时；按钮禁用防重复提交 |
| 图片含敏感信息经第三方模型 | 隐私 | 图片不落地、不入日志；与现有文本导入同等信任边界（文本同样发给同一模型）；在 AI 面板提示"截图将发送至已配置的 AI 服务" |

**Trade-off：一步式 vs 两步式（OCR 文本可编辑）**
- 一步式：延迟低、实现小，但误识别直接进表单（仅空字段）。
- 两步式：可校对，但双倍延迟与成本，且用户决策已选直接填充。
- **决策**：一步式 + 只填空字段 + 用户可手动修正。

## Migration Plan

- **无 schema 变更**：不建表、不改表；无 Flyway 迁移。
- **配置新增**：`application.yml` 增 `app.ai.image-request-timeout-seconds: ${AI_IMAGE_REQUEST_TIMEOUT_SECONDS:60}`；缺省即用 60，无需运维动作。
- **接口新增**：仅新增 POST 端点，无破坏性变更。
- **发布**：后端重新打包 + 前端构建；回滚 = revert 两个 commit，零数据风险。

## Open Questions

1. AI 配置页是否增加"视觉能力探测"按钮（发送 1×1 像素测试图）？本次不做，错误引导已覆盖主要痛点。
2. 是否记录识别成功率指标（成功 / 视觉不支持 / 其他失败计数）用于后续模型选型？本次不做。
3. 多图合并识别（一次粘贴多张截图拼为多 image_url part）？本次不做，协议上预留扩展空间（content 数组天然支持多 image part）。
