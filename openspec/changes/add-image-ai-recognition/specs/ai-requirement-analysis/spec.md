## Purpose

定义需求信息的 AI 提取能力：从用户提供的文本或需求截图中，识别并结构化提取提出人、部门、标题、类型、内容、所属系统、版本、时间周期等字段，经字典校验后返回；前端按"只填空字段、不覆盖已有内容"的规则填充表单。

## ADDED Requirements

### Requirement: 文本需求分析（基线）

系统 SHALL 提供 `POST /api/ai/analyze` 接口，接收纯文本，调用当前激活的 AI 配置，返回结构化需求字段；提取结果 SHALL 经过部门 / 需求类型 / 系统 / 版本字典校验与日期格式校验，无法确定的字段 SHALL 返回 null。

#### Scenario: 文本分析成功

- **WHEN** 用户提交一段需求描述文本且 AI 配置已启用并激活
- **THEN** 系统返回包含 requesterName、departmentId、title、typeId、content、systemId、targetVersionId、periodStartDate、periodEndDate 的结构化结果
- **AND** department / type 必须匹配字典中的启用项，否则该字段为 null

#### Scenario: AI 未启用或未配置

- **WHEN** 无激活配置、配置未启用、或缺少服务地址 / 模型名
- **THEN** 系统返回对应错误提示（"AI 功能未启用，请先在 AI 配置页开启并激活一个配置" / "AI 服务地址未配置" / "AI 模型名未配置"）

### Requirement: 图片需求分析

系统 SHALL 提供 `POST /api/ai/analyze-image` 接口（multipart，字段 `file`），接收单张需求截图，以 OpenAI 多模态消息格式（文本 prompt + image_url data URL）调用当前激活的 AI 配置，返回与文本分析**完全相同**的结构化结果。prompt SHALL 指示模型过滤截图中的无关噪音（聊天昵称、时间戳、表情、系统通知栏等），仅提取与需求相关的内容。

#### Scenario: 截图识别成功

- **WHEN** 用户上传或粘贴一张包含需求信息的截图（png / jpg / jpeg / webp，≤ 10 MB）
- **AND** 激活的 AI 模型支持视觉输入
- **THEN** 系统返回与 `/api/ai/analyze` 相同 schema 的结构化结果
- **AND** 结果同样经过字典与日期校验

#### Scenario: 微信聊天截图噪音过滤

- **WHEN** 截图为微信聊天记录（含昵称、头像、时间戳、表情等元素）
- **THEN** 模型 SHALL 仅提取需求语义内容；昵称 SHALL 不被误填为 requesterName，除非聊天上下文明确表明其为提出人

#### Scenario: 非图片文件被拒绝

- **WHEN** 上传文件的扩展名或魔数不属于 png / jpg / jpeg / webp
- **THEN** 系统拒绝请求并返回"仅支持 PNG / JPG / WebP 格式的图片"

#### Scenario: 超大图片被拒绝

- **WHEN** 图片字节数 > 10 × 1024 × 1024
- **THEN** 系统拒绝请求并返回"图片大小不能超过 10MB"

#### Scenario: 图片不落地

- **WHEN** 图片分析请求处理完成（无论成功或失败）
- **THEN** 系统 SHALL NOT 将图片写入任何存储目录、SHALL NOT 创建附件记录、SHALL NOT 将图片 base64 写入日志

### Requirement: 视觉能力错误引导

当激活模型不支持视觉输入时，系统 SHALL 返回可操作的引导性错误，而非泛化失败信息。

#### Scenario: 模型不支持图片识别

- **WHEN** AI 服务返回 4xx 错误且错误体包含 image / vision / multimodal / 图片 等关键词
- **THEN** 系统返回"当前激活模型可能不支持图片识别，请在 AI 配置中切换为视觉模型（如 qwen-vl、glm-4v、mimo-vl 等）后重试"

#### Scenario: 其他 AI 调用失败

- **WHEN** AI 服务返回非视觉相关的错误（网络、超时、5xx、解析失败）
- **THEN** 系统沿用现有文本分析的错误文案（SSL / 状态码 / 解析失败等）

### Requirement: 图片请求独立超时

系统 SHALL 为图片分析使用独立于文本分析的请求超时配置 `app.ai.image-request-timeout-seconds`（默认 60 秒）；文本分析超时 SHALL 保持 `app.ai.request-timeout-seconds`（默认 30 秒）不变。

#### Scenario: 视觉推理耗时超过文本超时

- **WHEN** 图片分析耗时在 30–60 秒之间
- **THEN** 请求 SHALL NOT 因文本超时配置被中断

### Requirement: 前端截图识别入口与填充

填写需求页的"AI 智能导入"面板 SHALL 提供截图识别入口：上传按钮（仅接受 image/*）与面板内 Ctrl+V 粘贴图片；识别成功后 SHALL 按"只填空字段、不覆盖已有内容"的规则填充表单，与文本导入的填充规则完全一致。

#### Scenario: 上传截图并识别填充

- **WHEN** 用户在 AI 面板点击上传按钮选择一张截图
- **THEN** 面板显示识别中 loading 状态
- **AND** 识别成功后仅填充当前为空的表单字段，message 提示"AI 已识别并填充 N 个字段"

#### Scenario: 面板内粘贴截图

- **WHEN** 用户在 AI 面板焦点范围内按下 Ctrl+V 且剪贴板含 image/* blob
- **THEN** 系统取该 blob 发起识别，行为与上传按钮一致

#### Scenario: 已有内容不被覆盖

- **WHEN** 表单中某字段已有用户输入的内容
- **AND** 识别结果中该字段非 null
- **THEN** 系统 SHALL NOT 覆盖该字段

#### Scenario: 识别结果全为 null

- **WHEN** 识别返回的所有字段均为 null
- **THEN** 系统提示"AI 未能从图片中识别出有效字段"，不修改任何表单字段

#### Scenario: 客户端预校验

- **WHEN** 用户选择或粘贴的文件非图片、或超过 10 MB
- **THEN** 前端 SHALL 在发起请求前拒绝并 message 提示，不调用后端接口

#### Scenario: 识别失败反馈

- **WHEN** 后端返回错误（AI 未启用 / 模型不支持视觉 / 服务异常）
- **THEN** 前端 SHALL 以 message.error 显示后端返回的具体 message
