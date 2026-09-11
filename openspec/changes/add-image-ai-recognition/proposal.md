## Why

填写需求时，用户手里常常只有一张截图——微信聊天记录、群里的需求描述、邮件截图。当前"AI 智能导入"只接受**文本**：用户必须先把截图里的文字手打或借助外部 OCR 工具转成文本，再粘贴进 AI 面板，流程断裂且容易抄错。系统已具备 OpenAI 兼容的 AI 配置与完整的结构化提取管线（字典校验 + 只填空字段），只差"把图片喂给模型"这一步。本变更让填写页直接上传/粘贴需求截图，调用系统配置的 AI 做视觉识别与结构化提取，一键填入表单，节约用户时间。

## What Changes

- **新增后端接口** `POST /api/ai/analyze-image`（multipart，字段 `file`）：接收单张图片（png / jpg / jpeg / webp，≤ 10 MB），以 OpenAI 多模态消息格式（`content` 数组 = 文本 prompt + `image_url` data URL）调用**当前激活的 AI 配置**，复用现有字典 prompt 与 `parseAndValidate` 校验管线，返回与 `/api/ai/analyze` 完全相同的 `AiAnalysisResponse`（9 字段）。
- **图片不落盘**：图片仅在后端内存中读取、转 base64、随请求发送，处理完即丢弃；不写入 uploads 目录、不建附件记录（隐私与存储成本考量）。
- **视觉能力错误引导**：当模型返回与图片/视觉/多模态相关的错误（HTTP 4xx 且错误体含 image/vision/multimodal 等关键词）时，返回明确提示"当前激活模型可能不支持图片识别，请在 AI 配置中切换为视觉模型（如 qwen-vl、glm-4v、mimo-vl 等）后重试"；其他错误沿用现有 AI 错误文案。
- **独立图片请求超时**：新增配置 `app.ai.image-request-timeout-seconds`（默认 60，文本分析仍为 30），避免视觉推理耗时被文本超时误杀。
- **前端 AI 面板新增"截图识别"入口**：在"AI 智能导入"折叠面板内新增上传按钮（隐藏 `<input type="file" accept="image/*">`）+ 支持在面板内 Ctrl+V 粘贴图片（复用浏览器剪贴板 items 解析，仅取 `image/*` blob）；识别期间显示 loading；识别成功后**直接填入空字段**（与现有文本导入行为一致：已有内容不覆盖），message 提示"AI 已识别并填充 N 个字段"；全部为 null 时提示"AI 未能从图片中识别出有效字段"。
- **前端填充逻辑复用**：将现有 `analyzeWithAi` 中"逐字段填空 + 计数"的逻辑抽为共享函数，文本导入与图片识别共用，保证两者填充规则永远一致。
- **客户端预校验**：非图片文件、超过 10 MB 的图片在前端即拒绝并 message 提示，不发起请求；服务端做同样的校验作为防线（含魔数校验）。

## Capabilities

### New Capabilities
- `ai-requirement-analysis`: 需求信息的 AI 提取能力。本次首次建立该 capability 的 spec：把现有"文本分析"作为基线 requirement 纳入，并新增"图片分析"requirement（输入约束、视觉调用、错误引导、不落地、填充规则）。

### Modified Capabilities
<!-- 无：openspec/specs 目录为空，本次不涉及对既有 spec 的修改 -->

## Impact

**受影响代码（后端）**
- `backend/src/main/java/com/xqfx/requirements/aiconfig/AiAnalysisService.java`：重构出可复用的 prompt 构建与结果解析；新增 `analyzeImage(MultipartFile)`；新增视觉消息构建与视觉错误映射。
- `backend/src/main/java/com/xqfx/requirements/aiconfig/AiAnalysisController.java`：新增 `POST /analyze-image` 端点。
- `backend/src/main/resources/application.yml`：新增 `app.ai.image-request-timeout-seconds`。

**受影响代码（前端）**
- `frontend/src/components/RequirementForm.vue`：AI 面板新增截图识别入口（上传 + 粘贴）、loading 状态、调用新接口、复用共享填充函数。

**API / 数据库 / 依赖**
- 新增 1 个 POST 端点；**无 schema 迁移**（图片不落盘、不建表）；**无新增第三方依赖**（后端用 JDK HttpClient 现成能力，前端用浏览器剪贴板 API）。

**兼容性**
- `/api/ai/analyze` 文本接口行为完全不变；现有 AI 智能导入面板功能不受影响。
- 激活模型不支持视觉时：功能降级为"清晰报错 + 换模型引导"，不影响文本导入。

**安全与隐私**
- 图片魔数校验（png/jpg/webp 头），拒绝伪装文件；10 MB 上限防 DoS。
- 图片不持久化、不写日志（base64 不进入 log）。
- 复用现有鉴权（登录用户才能调用 `/api/ai/*`）。

**开放问题（不阻塞交付）**
1. 是否为识别结果提供"预览确认弹窗"交互？本次按用户决策采用"直接填空字段"，预览交互留作后续可选增强。
2. 是否支持一次上传多张截图合并识别？本次仅单张，多张留作后续。
3. 是否在附件区图片条目上加"AI 识别"联动按钮？本次按用户决策不做，留作后续。
