package com.xqfx.requirements.requirement;

import com.xqfx.requirements.system.SystemEntity;
import com.xqfx.requirements.system.SystemProfile;
import com.xqfx.requirements.system.SystemRepository;
import com.xqfx.requirements.system.SystemVersionEntity;
import com.xqfx.requirements.system.SystemVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:requirement-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RequirementApiTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;
    @org.springframework.beans.factory.annotation.Autowired
    private SystemRepository systemRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private SystemVersionRepository versionRepository;

    private Long systemId;
    private Long versionId;

    @BeforeEach
    void createSystemAndVersion() {
        var system = systemRepository.save(new SystemEntity(SystemProfile.create("客户管理系统-" + java.util.UUID.randomUUID(), "李明", List.of())));
        systemId = system.id();
        versionId = versionRepository.save(new SystemVersionEntity(system, "V1.0")).id();
    }

    @Test
    void createsSubmittedBugRequirementForSystemVersion() throws Exception {
        mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"张三","department":"市场部","title":"客户导入失败","type":"BUG","content":"导入 Excel 后提示异常","systemId":%d,"targetVersionId":%d,"periodStartDate":"2026-07-10","periodEndDate":"2026-07-20"}
                                """.formatted(systemId, versionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("BUG"))
                .andExpect(jsonPath("$.status").value("PENDING_EVALUATION"))
                .andExpect(jsonPath("$.targetVersionId").value(versionId))
                .andExpect(jsonPath("$.periodStartDate").value("2026-07-10"))
                .andExpect(jsonPath("$.periodEndDate").value("2026-07-20"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.submittedAt").isNotEmpty())
                .andExpect(jsonPath("$.statusUpdatedAt").isNotEmpty());
    }

    @Test
    void filtersRequirementsByType() throws Exception {
        mockMvc.perform(post("/api/requirements")
                .contentType("application/json")
                .content("""
                        {"requesterName":"王芳","department":"财务部","title":"报销错误","type":"BUG","content":"保存失败"}
                        """));

        mockMvc.perform(get("/api/requirements").param("type", "BUG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("BUG"));
    }

    @Test
    void filtersRequirementsBySystem() throws Exception {
        mockMvc.perform(post("/api/requirements")
                .contentType("application/json")
                .content("""
                        {"requesterName":"赵敏","department":"市场部","title":"客户标签","type":"REQUIREMENT","content":"增加客户标签","systemId":%d}
                        """.formatted(systemId)));

        mockMvc.perform(get("/api/requirements").param("systemId", systemId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("REQUIREMENT"));
    }

    @Test
    void updatesRequirementStatus() throws Exception {
        var created = mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"李四","department":"研发部","title":"状态流转","type":"REQUIREMENT","content":"更新处理状态"}
                                """))
                .andReturn();
        var id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(patch("/api/requirements/{id}/status", id)
                        .contentType("application/json")
                        .content("{" + "\"status\":\"IN_DEVELOPMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_DEVELOPMENT"));
    }

    @Test
    void getsRequirementDetailById() throws Exception {
        var created = mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"陈晨","department":"产品部","title":"详情展示","type":"REQUIREMENT","content":"查看完整需求内容","systemId":%d,"targetVersionId":%d,"periodStartDate":"2026-07-10","periodEndDate":"2026-07-20"}
                                """.formatted(systemId, versionId)))
                .andReturn();
        var id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(get("/api/requirements/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Long.parseLong(id)))
                .andExpect(jsonPath("$.requesterName").value("陈晨"))
                .andExpect(jsonPath("$.department").value("产品部"))
                .andExpect(jsonPath("$.title").value("详情展示"))
                .andExpect(jsonPath("$.content").value("查看完整需求内容"))
                .andExpect(jsonPath("$.systemId").value(systemId))
                .andExpect(jsonPath("$.targetVersionId").value(versionId));
    }

    @Test
    void updatesRequirementDetails() throws Exception {
        var created = mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"周杰","department":"运营部","title":"原始标题","type":"BUG","content":"原始内容","systemId":%d,"targetVersionId":%d}
                                """.formatted(systemId, versionId)))
                .andReturn();
        var id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(put("/api/requirements/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"周杰伦","department":"研发部","title":"更新后的标题","type":"REQUIREMENT","content":"更新后的详细内容","systemId":%d,"targetVersionId":%d,"periodStartDate":"2026-07-12","periodEndDate":"2026-07-18","status":"CONFIRMED"}
                                """.formatted(systemId, versionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requesterName").value("周杰伦"))
                .andExpect(jsonPath("$.department").value("研发部"))
                .andExpect(jsonPath("$.title").value("更新后的标题"))
                .andExpect(jsonPath("$.type").value("REQUIREMENT"))
                .andExpect(jsonPath("$.content").value("更新后的详细内容"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.periodStartDate").value("2026-07-12"))
                .andExpect(jsonPath("$.periodEndDate").value("2026-07-18"));
    }

    @Test
    void softDeletesRequirement() throws Exception {
        var created = mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"孙倩","department":"测试部","title":"待删除需求","type":"BUG","content":"该需求需要软删除"}
                                """))
                .andReturn();
        var id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(delete("/api/requirements/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/requirements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
        mockMvc.perform(get("/api/requirements/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsSubmittedRequirementWithoutType() throws Exception {
        mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"赵丽","department":"行政部","title":"缺少类型","content":"正式需求必须选择类型"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsIncompleteDraftAndReadsItBack() throws Exception {
        var created = mockMvc.perform(post("/api/requirements/drafts")
                        .contentType("application/json")
                        .content("""
                                {"title":"未完成草稿"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saveType").value("DRAFT"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andReturn();
        var id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(get("/api/requirements/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("未完成草稿"))
                .andExpect(jsonPath("$.saveType").value("DRAFT"));
    }

    @Test
    void updatesDraftWithoutFormalFieldValidation() throws Exception {
        var created = mockMvc.perform(post("/api/requirements/drafts")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn();
        var id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(put("/api/requirements/{id}/draft", id)
                        .contentType("application/json")
                        .content("""
                                {"title":"补充后的草稿","content":"尚未填写其他必填字段"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("补充后的草稿"))
                .andExpect(jsonPath("$.content").value("尚未填写其他必填字段"))
                .andExpect(jsonPath("$.saveType").value("DRAFT"));
    }

    @Test
    void convertsCompleteDraftToSubmittedRequirement() throws Exception {
        var created = mockMvc.perform(post("/api/requirements/drafts")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn();
        var id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(put("/api/requirements/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"吴迪","department":"客服部","title":"草稿转正式","type":"REQUIREMENT","content":"这是一条已经填写完整的正式需求"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saveType").value("SUBMITTED"))
                .andExpect(jsonPath("$.status").value("PENDING_EVALUATION"));
    }

    @Test
    void filtersRequirementsBySaveType() throws Exception {
        mockMvc.perform(post("/api/requirements/drafts")
                .contentType("application/json")
                .content("{\"title\":\"草稿筛选\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/requirements")
                .contentType("application/json")
                .content("""
                        {"requesterName":"刘洋","department":"采购部","title":"正式筛选","type":"BUG","content":"用于验证保存类型筛选"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/requirements").param("saveType", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.saveType == 'DRAFT')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.saveType != 'DRAFT')]").isEmpty());
    }

    @Test
    void rejectsNewRequirementForInactiveSystem() throws Exception {
        var createdSystem = mockMvc.perform(post("/api/systems")
                        .contentType("application/json")
                        .content("""
                                {"name":"已停用系统","ownerName":"负责人","collaborators":[]}
                                """))
                .andReturn();
        var inactiveSystemId = com.jayway.jsonpath.JsonPath.read(createdSystem.getResponse().getContentAsString(), "$.id").toString();
        mockMvc.perform(patch("/api/systems/{id}/status", inactiveSystemId)
                        .contentType("application/json")
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"马超","department":"支持部","title":"停用系统需求","type":"BUG","content":"停用系统不能接收新的正式需求","systemId":%s}
                                """.formatted(inactiveSystemId)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsNewRequirementForInactiveTargetVersion() throws Exception {
        mockMvc.perform(patch("/api/system-versions/{id}/status", versionId)
                        .contentType("application/json")
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"黄伟","department":"运维部","title":"停用版本需求","type":"REQUIREMENT","content":"停用版本不能作为新的目标版本","systemId":%d,"targetVersionId":%d}
                                """.formatted(systemId, versionId)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsChangingRequirementToInactiveSystem() throws Exception {
        var inactiveSystem = mockMvc.perform(post("/api/systems")
                        .contentType("application/json")
                        .content("""
                                {"name":"编辑目标停用系统","ownerName":"负责人","collaborators":[]}
                                """))
                .andReturn();
        var inactiveSystemId = com.jayway.jsonpath.JsonPath.read(inactiveSystem.getResponse().getContentAsString(), "$.id").toString();
        mockMvc.perform(patch("/api/systems/{id}/status", inactiveSystemId)
                        .contentType("application/json")
                        .content("{" + "\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        var requirement = mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"编辑用户","department":"研发部","title":"编辑系统校验","type":"BUG","content":"不能主动更换到停用系统","systemId":%d}
                                """.formatted(systemId)))
                .andReturn();
        var requirementId = com.jayway.jsonpath.JsonPath.read(requirement.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(put("/api/requirements/{id}", requirementId)
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"编辑用户","department":"研发部","title":"编辑系统校验","type":"BUG","content":"不能主动更换到停用系统","systemId":%s}
                                """.formatted(inactiveSystemId)))
                .andExpect(status().isConflict());
    }

    @Test
    void createsAndAssociatesNewSystemWhenSubmittingRequirement() throws Exception {
        var created = mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"陈琳","department":"产品部","title":"新系统需求","type":"REQUIREMENT","content":"正式保存时应同时创建新系统","newSystem":{"name":"新客户系统","ownerName":"孙明","collaborators":["王红","李军"]}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.systemId").isNumber())
                .andReturn();
        var newSystemId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.systemId").toString();

        mockMvc.perform(get("/api/systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + newSystemId + " && @.name == '新客户系统' && @.ownerName == '孙明')]").isNotEmpty());
    }

    @Test
    void returnsBadRequestWhenPeriodEndPrecedesStart() throws Exception {
        mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"杨晨","department":"销售部","title":"无效日期范围","type":"BUG","content":"需求周期结束日期不能早于开始日期","periodStartDate":"2026-07-20","periodEndDate":"2026-07-10"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("结束日期不能早于开始日期"));
    }

    @Test
    void pagesRequirements() throws Exception {
        mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"分页用户","department":"信息部","title":"分页需求","type":"REQUIREMENT","content":"用于验证分页接口的需求记录"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/requirements/page").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void combinesRequirementPageFilters() throws Exception {
        var created = mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"组合用户","department":"产品部","title":"组合筛选需求","type":"REQUIREMENT","content":"通过多个筛选条件定位此需求","systemId":%d,"targetVersionId":%d}
                                """.formatted(systemId, versionId)))
                .andReturn();
        var requirementId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();
        mockMvc.perform(patch("/api/requirements/{id}/status", requirementId)
                        .contentType("application/json")
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"其他用户","department":"产品部","title":"另一条需求","type":"BUG","content":"不应命中组合筛选","systemId":%d}
                                """.formatted(systemId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/requirements/page")
                        .param("page", "0").param("size", "20")
                        .param("systemId", systemId.toString())
                        .param("department", "产品部")
                        .param("requesterName", "组合用户")
                        .param("type", "REQUIREMENT")
                        .param("status", "CONFIRMED")
                        .param("saveType", "SUBMITTED")
                        .param("keyword", "组合筛选"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(Long.parseLong(requirementId)));
    }

    @Test
    void filtersRequirementsBySubmittedDateRange() throws Exception {
        mockMvc.perform(post("/api/requirements").contentType("application/json").content("""
                {"requesterName":"日期用户","department":"研发部","title":"日期筛选","type":"BUG","content":"验证填写时间范围筛选"}
                """)).andExpect(status().isCreated());
        var tomorrow = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1).toString();

        mockMvc.perform(get("/api/requirements/page").param("page", "0").param("size", "20").param("submittedFrom", tomorrow))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void filtersRequirementsByOverlappingPeriod() throws Exception {
        mockMvc.perform(post("/api/requirements").contentType("application/json").content("""
                {"requesterName":"周期用户","department":"研发部","title":"周期筛选","type":"REQUIREMENT","content":"验证周期重叠筛选","periodStartDate":"2026-07-10","periodEndDate":"2026-07-20"}
                """)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/requirements").contentType("application/json").content("""
                {"requesterName":"其他周期","department":"研发部","title":"其他周期","type":"REQUIREMENT","content":"不应命中","periodStartDate":"2026-08-01","periodEndDate":"2026-08-10"}
                """)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/requirements/page").param("page", "0").param("size", "20").param("periodOverlapStart", "2026-07-15").param("periodOverlapEnd", "2026-07-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.title == '周期筛选')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.title == '其他周期')]").isEmpty());
    }

    @Test
    void filtersRequirementsWithoutAssignedSystem() throws Exception {
        mockMvc.perform(post("/api/requirements").contentType("application/json").content("""
                {"requesterName":"无系统用户","department":"研发部","title":"暂无系统需求","type":"BUG","content":"用于验证暂无系统筛选"}
                """)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/requirements").contentType("application/json").content("""
                {"requesterName":"有系统用户","department":"研发部","title":"已有系统需求","type":"BUG","content":"不应命中暂无系统筛选","systemId":%d}
                """.formatted(systemId))).andExpect(status().isCreated());

        mockMvc.perform(get("/api/requirements/page").param("page", "0").param("size", "100").param("unassignedSystem", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.title == '暂无系统需求')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.title == '已有系统需求')]").isEmpty());
    }

    @Test
    void uploadsSupportedAttachmentForRequirement() throws Exception {
        var created = mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"附件用户","department":"研发部","title":"附件需求","type":"BUG","content":"上传附件并保存到本地目录"}
                                """))
                .andReturn();
        var requirementId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();
        var file = new org.springframework.mock.web.MockMultipartFile("file", "示例.png", "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/requirements/{id}/attachments", requirementId).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalName").value("示例.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void downloadsUploadedAttachment() throws Exception {
        var created = mockMvc.perform(post("/api/requirements").contentType("application/json").content("""
                {"requesterName":"下载用户","department":"研发部","title":"下载附件","type":"BUG","content":"验证附件下载"}
                """)).andReturn();
        var requirementId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();
        var file = new org.springframework.mock.web.MockMultipartFile("file", "说明.pdf", "application/pdf", new byte[] {4, 5, 6});
        var uploaded = mockMvc.perform(multipart("/api/requirements/{id}/attachments", requirementId).file(file)).andReturn();
        var attachmentId = com.jayway.jsonpath.JsonPath.read(uploaded.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(get("/api/attachments/{id}", attachmentId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "application/pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(new byte[] {4, 5, 6}));
    }

    @Test
    void softDeletesAttachment() throws Exception {
        var created = mockMvc.perform(post("/api/requirements").contentType("application/json").content("""
                {"requesterName":"删除附件用户","department":"研发部","title":"删除附件","type":"BUG","content":"验证附件软删除"}
                """)).andReturn();
        var requirementId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();
        var uploaded = mockMvc.perform(multipart("/api/requirements/{id}/attachments", requirementId).file(new org.springframework.mock.web.MockMultipartFile("file", "删除.pdf", "application/pdf", new byte[] {9}))).andReturn();
        var attachmentId = com.jayway.jsonpath.JsonPath.read(uploaded.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(delete("/api/attachments/{id}", attachmentId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/attachments/{id}", attachmentId)).andExpect(status().isNotFound());
    }
}
