# 需求版本集中管理实施计划

## 目标

实现已确认的需求版本集中管理设计：新增需求不选择版本，版本增加说明，处理员和管理员在独立页面批量绑定、迁移或解除正式需求；草稿完全排除；候选状态固定为待评估、已确认、开发中、暂停；页面遵循 `docs/版本需求管理页视觉设计稿.html`。

## 实施原则

- 保留现有历史需求与版本关联。
- 普通需求编辑只在所属系统变化时清空旧版本。
- 批量绑定和解除必须全有或全无，并校验每条需求的 `recordVersion`。
- 所有版本变化写入结构化历史。
- 后端先写失败测试再实现；按用户约定，任务结束前删除本次新增的临时测试文件。
- 前端以类型检查、构建和 Playwright 真实浏览器流程验证，不引入新的测试框架依赖。

## 任务 1：数据库和版本领域模型

涉及文件：

- `backend/src/main/resources/db/migration/V20__requirement_version_management.sql`
- `backend/src/main/java/com/xqfx/requirements/system/SystemVersionEntity.java`
- `backend/src/main/java/com/xqfx/requirements/system/SystemVersionResponse.java`
- `backend/src/main/java/com/xqfx/requirements/system/SystemVersionController.java`
- `backend/src/main/java/com/xqfx/requirements/system/SystemVersionManagementController.java`
- `backend/src/main/java/com/xqfx/requirements/system/SystemVersionService.java`

步骤：

1. 临时新增版本领域测试，覆盖说明创建、编辑和版本乐观锁。
2. 新增 `description` 和 `record_version` 数据库字段。
3. 扩展实体、响应和创建、编辑、启停请求。
4. 增加单个版本详情接口及版本需求统计字段。
5. 运行后端测试确认通过。

## 任务 2：版本变更历史

涉及文件：

- `backend/src/main/java/com/xqfx/requirements/requirement/RequirementVersionChangeEntity.java`
- `backend/src/main/java/com/xqfx/requirements/requirement/RequirementVersionChangeRepository.java`
- `backend/src/main/java/com/xqfx/requirements/requirement/RequirementVersionChangeResponse.java`
- `backend/src/main/java/com/xqfx/requirements/requirement/RequirementVersionChangeAction.java`
- `backend/src/main/java/com/xqfx/requirements/requirement/RequirementController.java`
- `backend/src/main/java/com/xqfx/requirements/requirement/RequirementService.java`

步骤：

1. 临时新增历史记录测试，覆盖绑定、迁移和解除名称快照。
2. 增加历史实体、仓库和响应。
3. 增加需求版本历史查询接口，复用现有需求查看权限。
4. 将版本变更记录写入统一服务方法，避免各调用点自行拼装。

## 任务 3：需求创建和编辑语义

涉及文件：

- `backend/src/main/java/com/xqfx/requirements/requirement/RequirementController.java`
- `backend/src/main/java/com/xqfx/requirements/requirement/RequirementService.java`
- `backend/src/main/java/com/xqfx/requirements/requirement/RequirementEntity.java`
- `frontend/src/components/RequirementForm.vue`
- `frontend/src/components/RequirementEditor.vue`

步骤：

1. 临时新增测试，证明新需求和草稿版本为空、普通编辑保留历史版本、换系统清空版本并记录。
2. 从创建、草稿和普通更新 DTO 中移除 `targetVersionId`。
3. 调整实体更新方法：系统相同则保留版本，系统变化则清空版本。
4. 从新增和编辑表单移除目标版本控件、加载逻辑和请求字段。
5. 编辑已有绑定需求时保留只读版本展示和换系统确认。

## 任务 4：版本需求查询与批量操作接口

涉及文件：

- `backend/src/main/java/com/xqfx/requirements/requirement/SystemVersionRequirementController.java`
- `backend/src/main/java/com/xqfx/requirements/requirement/SystemVersionRequirementService.java`
- `backend/src/main/java/com/xqfx/requirements/requirement/SystemVersionRequirementResponse.java`
- `backend/src/main/java/com/xqfx/requirements/requirement/RequirementRepository.java`
- `backend/src/main/java/com/xqfx/requirements/requirement/RequirementSpecifications.java`
- `backend/src/main/java/com/xqfx/requirements/requirement/RequirementEntity.java`

步骤：

1. 临时新增服务测试，覆盖候选范围、草稿排除、四个允许状态、跨系统、终态、停用版本、并发冲突和整批回滚。
2. 增加当前版本和候选需求分页查询。
3. 候选查询固定 `saveType = SUBMITTED`，状态固定为四个非终态。
4. 当前版本列表只查询正式需求；终态需求可见但不可勾选。
5. 实现批量绑定、迁移和解除接口，返回绑定数、迁移数或解除数。
6. 在同一事务内逐条校验和变更，写入历史后统一提交。

## 任务 5：系统迁移的一致性

涉及文件：

- `backend/src/main/java/com/xqfx/requirements/system/SystemController.java`
- `backend/src/main/java/com/xqfx/requirements/system/SystemService.java`
- `backend/src/main/java/com/xqfx/requirements/requirement/SystemVersionRequirementService.java`
- `backend/src/main/java/com/xqfx/requirements/requirement/RequirementRepository.java`

步骤：

1. 将现有直接批量更新改为受控的事务迁移。
2. 对每条存在目标版本的需求记录解除历史。
3. 更新需求所属系统、清空版本并递增乐观锁版本。
4. 保留原有迁移数量响应和目标系统校验。

## 任务 6：系统与版本入口

涉及文件：

- `frontend/src/components/SystemManagement.vue`
- `frontend/src/router/index.ts`

步骤：

1. 版本表单增加说明字段和 `recordVersion`。
2. 版本卡片展示说明摘要。
3. 需求处理员和管理员看到“管理需求”入口。
4. 新增 `/versions/:versionId` 路由，限定 `HANDLER` 和 `ADMIN`。

## 任务 7：独立版本需求管理页

涉及文件：

- `frontend/src/components/VersionRequirementManagement.vue`
- `frontend/src/constants/statusConfig.ts`
- `frontend/src/constants/urgencyConfig.ts`

步骤：

1. 按视觉稿实现面包屑、版本信息横幅、统计区和返回按钮。
2. 实现“当前版本需求”和“可纳入需求”标签页。
3. 实现关键字、状态、紧急程度和来源筛选。
4. 状态控件默认“全部状态”，具体选项仅四个非终态。
5. 实现跨页选择清理、批量绑定确认、迁移数量提示和批量解除确认。
6. 终态行禁用复选框并显示只读提示。
7. 页面在窄屏下保持内容不重叠，表格允许横向滚动。

## 任务 8：需求详情历史

涉及文件：

- `frontend/src/components/RequirementDetail.vue`

步骤：

1. 加载版本变更历史。
2. 展示绑定、迁移和解除的版本名称、操作人和时间。
3. 无历史记录时不增加空白噪声。

## 任务 9：验证和清理

1. 运行 `mvn test`。
2. 删除本次新增的临时测试文件，再次运行现有 `mvn test`。
3. 运行 `npm run build`。
4. 启动后端和前端开发服务。
5. 使用 Playwright 验证处理员流程：创建无版本需求、进入版本页、筛选候选、批量绑定、跨版本迁移、解除绑定和查看历史。
6. 验证草稿不出现在候选列表，状态选项只有四个非终态。
7. 对照视觉稿检查桌面和移动视口截图，确认无空白、遮挡、溢出或交互失效。
8. 检查 `git diff --check` 和工作区，确保不提交日志、构建产物、临时测试和用户现有未跟踪文件。

## 完成标准

- 设计文档中的 12 条验收标准全部有代码、测试或浏览器证据支持。
- 新页面与视觉稿的结构、层次、筛选和批量确认交互一致。
- 草稿在查询和写接口两侧都被排除。
- 四个非终态是候选查询和状态选择的唯一具体状态集合。
- 前后端构建、测试和真实浏览器流程全部通过。

