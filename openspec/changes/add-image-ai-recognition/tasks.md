## 1. 后端：AiAnalysisService 重构与图片分析

- [x] 1.1 重构 `AiAnalysisService.buildPrompt`：拆出字典上下文构建（部门 / 类型 / 系统版本 + JSON schema + 规则 1–7）为 `buildDictionaryContext()`，文本路径与图片路径共用（design.md D3）  <!-- 实施为 buildExtractionPrompt(departments, types, userContentSection)，文本路径传"用户文本：\n"+text，输出与重构前逐字一致 -->
- [x] 1.2 新增图片路径 prompt 段：微信 / QQ 聊天 / 邮件 / 文档截图说明 + 噪音过滤指令（忽略昵称 / 头像 / 时间戳 / 表情 / 系统元素；requesterName 强约束；无关文字不提取）  <!-- IMAGE_INPUT_INSTRUCTION 常量 -->
- [x] 1.3 抽出纯函数 `mapVisionError(int status, String body)`：status ∈ [400,500) 且 body 小写含 image/vision/visual/multimodal/图片/视觉 任一关键词 → 返回引导文案"当前激活模型可能不支持图片识别，请在 AI 配置中切换为视觉模型（如 qwen-vl、glm-4v、mimo-vl 等）后重试"；否则返回 null（design.md D5）
- [x] 1.4 改造 `callModel`：新增重载或参数 `visionParts`，非 2xx 时读取响应体并调用 `mapVisionError`（仅图片路径启用映射；文本路径行为不变）
- [x] 1.5 新增 `callModelWithImage(serviceUrl, modelName, apiKey, prompt, dataUrl, timeoutSeconds)`：构建 `content` 数组 `[{type:text},{type:image_url,image_url:{url:dataUrl}}]`（design.md D2）  <!-- 实施为 callModel 六参重载 -->
- [x] 1.6 新增图片魔数识别私有方法：png `89 50 4E 47`、jpg `FF D8 FF`、webp `RIFF....WEBP`；返回 contentType 或 null（design.md D4）  <!-- detectImageContentType -->
- [x] 1.7 新增 `analyzeImage(MultipartFile file)`：校验 isEmpty → ≤ 10 MB（文案"图片大小不能超过 10MB"）→ 魔数（文案"仅支持 PNG / JPG / WebP 格式的图片"）→ 读字节转 base64 data URL → 调用视觉模型 → `parseAndValidate` 复用；图片字节与 base64 不写日志、不落盘（design.md D4、spec "图片不落地"）
- [x] 1.8 注入 `@Value("${app.ai.image-request-timeout-seconds:60}")`，`analyzeImage` 全链路使用该超时（design.md D6）

## 2. 后端：Controller 与配置

- [x] 2.1 `AiAnalysisController` 新增 `POST /analyze-image`：`@RequestParam("file") MultipartFile file`，异常映射与 `/analyze` 一致（AiAnalysisException → 502 BAD_GATEWAY + message）
- [x] 2.2 `application.yml` 的 `app.ai` 下新增 `image-request-timeout-seconds: ${AI_IMAGE_REQUEST_TIMEOUT_SECONDS:60}`

## 3. 后端：单元测试（手动编译方式）

- [x] 3.1 为 `mapVisionError` 写单元测试：覆盖 400+含 vision 关键词 → 引导文案；400+无关 body → null；500+含 image → null（仅 4xx 映射）；中文关键词"图片"命中  <!-- 曾完成 15/15 全绿；2026-09-11 用户决策删除全部新增测试文件（项目维持零新增测试），验证结论以 browser-use 实测证据为准 -->
- [x] 3.2 为图片魔数识别写单元测试：png / jpg / webp 头通过；gif / bmp / 文本伪装 png 拒绝  <!-- 同上：已按用户决策删除 -->
- [x] 3.3 为大小校验写单元测试：10 MB + 1 字节 → 拒绝文案；恰好 10 MB → 通过校验进入下一步  <!-- 实施为纯函数 checkImageSize；测试文件已按用户决策删除 -->
- [x] 3.4 沿用 `.workbuddy/memory/2026-09-11.md` 的手动编译 + junit-platform-launcher 流程运行上述测试并全绿  <!-- 当时 15/15 通过；测试与运行器后续已按用户决策删除 -->

## 4. 前端：RequirementForm.vue 截图识别入口

- [x] 4.1 抽共享填充函数 `applyAiAnalysis(data): number`（逐字段 if 空则填 + 计数），`analyzeWithAi` 改为调用它（design.md D8）
- [x] 4.2 AI 折叠面板内新增"识别需求截图"按钮（`:loading="aiImageAnalyzing"`）+ 隐藏 `<input type="file" accept="image/png,image/jpeg,image/webp">`；按钮旁提示"截图将发送至已配置的 AI 服务"
- [x] 4.3 实现 `analyzeImageFile(file: File)`：客户端预校验（type 白名单 + ≤ 10 MB，不通过 message.warning 不发请求）→ FormData 上传 `POST /ai/analyze-image` → 成功调用 `applyAiAnalysis` → filled > 0 提示"AI 已识别并填充 N 个字段"、filled === 0 提示"AI 未能从图片中识别出有效字段"；失败 message.error 显示后端 message
- [x] 4.4 AI 面板内容区挂 `@paste="onAiPanelPaste"`：同步遍历 `clipboardData.items` 取第一个 image/* blob；取到时 `preventDefault` + `stopPropagation`（阻断冒泡到根 section 附件粘贴监听，design.md D7）并调用 `analyzeImageFile`；取不到时不拦截（AI 文本框普通文本粘贴不受影响）  <!-- 挂在 .ai-import-body 包装 div -->
- [x] 4.5 识别期间禁用"分析并填充"与"识别需求截图"按钮，防重复提交

## 5. 端到端验证（browser-use 真实浏览器）

- [x] 5.1 填写需求页：AI 面板出现"识别需求截图"按钮与隐私提示文案  <!-- browser-use 验证：按钮文本/accept/双提示文案均在 DOM -->
- [x] 5.2 上传一张 png 截图：Network 面板可见 `POST /api/ai/analyze-image`（multipart）；按钮 loading；响应后按模型能力出现"填充 N 个字段"或引导性错误文案  <!-- 实测 mimo-v2.5 支持视觉：含文字截图返回 张三/标题/内容/typeId=12；resource timing 记录到 5 次 analyze-image 请求 -->
- [x] 5.3 AI 面板内 Ctrl+V 粘贴截图：触发同一接口；附件区待上传队列**不**新增该图片（stopPropagation 生效）  <!-- 粘贴后 analyze-image 4→5，附件队列 0→0 -->
- [x] 5.4 附件区 dropzone 内 Ctrl+V 粘贴截图：附件队列正常新增，AI 识别**不**被触发（反向隔离）  <!-- analyze-image 5→5，附件队列 +1（截图-20260911-154736.png） -->
- [x] 5.5 AI 文本框内 Ctrl+V 粘贴纯文本：文本正常插入文本框，不触发图片识别  <!-- defaultPrevented=false，无新请求 -->
- [x] 5.6 选择非图片文件（.txt）与超 10 MB 图片：前端 message.warning 拒绝，Network 无请求  <!-- "仅支持 PNG / JPG / WebP 格式的图片" / "图片大小不能超过 10MB"，请求数不变 -->
- [x] 5.7 回归：现有文本"分析并填充"行为不变（只填空字段 + 计数提示）  <!-- 文本导入返回"AI 已识别并填充 3 个字段"，已有标题未被覆盖 -->
- [ ] 5.8 截图留证：AI 面板入口、识别 loading、成功 / 引导错误 message 各一张  <!-- 阻塞：IDE Browser 视图被隐藏（NATIVE_BROWSER_VIEWPORT_UNAVAILABLE），无法截图；message 与 loading 的 DOM 文本证据已在本会话归档（含引导文案"当前激活模型可能不支持图片识别…"实测触发记录）。待用户重开 Browser 视图后补拍 -->

## 6. 文档与收尾

- [x] 6.1 更新 `docs/技术实现方案.md`：AI 章节补充图片识别接口、超时配置、错误引导说明  <!-- 6.2 接口清单新增 AI 表 + 新增 6.5 节 -->
- [x] 6.2 更新 `.workbuddy/memory/<当日>.md`：记录 mimo-v2.5 视觉能力实测结论、mapVisionError 关键词表、AI 面板与附件区 paste 隔离方案
- [x] 6.3 `npm run build` 零 TS 错误；后端手动编译零错误  <!-- vue-tsc + vite build 通过；javac 主代码 + 测试编译通过；15/15 单测绿 -->
- [ ] 6.4 PR 描述：覆盖 spec requirements 清单、验证证据截图、开放问题 1–3 处置状态（均不做）  <!-- 需用户创建 PR 时填写；模板见下 -->

### 6.4 PR 描述模板（可直接复制）

```markdown
## 需求截图 AI 识别（add-image-ai-recognition）

### 覆盖的 spec requirements（specs/ai-requirement-analysis/spec.md）
- 文本需求分析（基线，行为不变）
- 图片需求分析（multipart / 魔数 / 10MB / 不落地 / 字典校验复用）
- 视觉能力错误引导（4xx + 关键词 → 换模型引导文案）
- 图片请求独立超时（60s）
- 前端截图识别入口与填充（上传 + 面板粘贴 / 只填空字段 / 预校验 / 失败反馈）

### 验证证据
- 后端单测曾 15/15 绿（mapVisionError / detectImageContentType / checkImageSize），后按用户决策删除全部新增测试文件，项目维持零新增测试
- browser-use 实测：mimo-v2.5 支持视觉，含文字截图识别出 张三/标题/内容/typeId 并填充 3 字段
- 视觉兜底实测：伪造 png 触发厂商 4xx → 引导文案正确显示
- paste 隔离双向验证：AI 面板粘贴不进附件队列；附件区粘贴不触发识别
- 预校验：.txt / 超 10MB 拒绝且零请求；文本导入回归通过
- 待补：5.8 截图留证（IDE Browser 视图隐藏，重开后补拍）

### 开放问题处置
1. 预览确认弹窗：不做（用户决策直接填充）
2. 多图合并：不做
3. 附件区联动按钮：不做（用户决策）

### 变更文件
- 后端：AiAnalysisService.java（+analyzeImage/视觉消息/mapVisionError/魔数/超时）、AiAnalysisController.java（+端点）、application.yml（+超时配置）
- 后端测试：AiAnalysisServiceImageTest.java + TestRunner.java  <!-- 已按用户决策删除，仅保留此行历史记录 -->
- 前端：RequirementForm.vue（applyAiAnalysis 复用 + 截图识别入口 + 面板 paste）
- 文档：docs/技术实现方案.md 6.2/6.5、.workbuddy/memory/2026-09-11.md

### 回滚
revert 前后端 commit 即可；无 schema 迁移、无配置破坏性变更。
```
