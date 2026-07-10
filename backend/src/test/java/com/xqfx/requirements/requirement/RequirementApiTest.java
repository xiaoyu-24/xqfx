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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:requirement-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
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
        var system = systemRepository.save(new SystemEntity(SystemProfile.create("客户管理系统", "李明", List.of())));
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
                .andExpect(jsonPath("$.periodEndDate").value("2026-07-20"));
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
}
