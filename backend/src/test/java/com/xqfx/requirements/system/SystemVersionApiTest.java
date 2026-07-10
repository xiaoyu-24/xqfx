package com.xqfx.requirements.system;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:versions;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SystemVersionApiTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @org.springframework.beans.factory.annotation.Autowired
    private SystemRepository systemRepository;

    private Long systemId;

    @BeforeEach
    void createSystem() {
        var saved = systemRepository.save(new SystemEntity(
                SystemProfile.create("客户管理系统-" + java.util.UUID.randomUUID(), "李明", java.util.List.of())
        ));
        systemId = saved.id();
    }

    @Test
    void createsVersionForSystem() throws Exception {
        mockMvc.perform(post("/api/systems/{systemId}/versions", systemId)
                        .contentType("application/json")
                        .content("{" + "\"name\":\"V1.0\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.systemId").value(systemId))
                .andExpect(jsonPath("$.name").value("V1.0"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void listsVersionsForSystem() throws Exception {
        mockMvc.perform(post("/api/systems/{systemId}/versions", systemId)
                .contentType("application/json")
                .content("{" + "\"name\":\"V2.0\"}"));

        mockMvc.perform(get("/api/systems/{systemId}/versions", systemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'V2.0')]").exists());
    }

    @Test
    void updatesVersionName() throws Exception {
        var created = mockMvc.perform(post("/api/systems/{systemId}/versions", systemId)
                        .contentType("application/json")
                        .content("{\"name\":\"V1.0\"}"))
                .andReturn();
        var versionId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(put("/api/system-versions/{id}", versionId)
                        .contentType("application/json")
                        .content("{\"name\":\"V1.1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("V1.1"));
    }

    @Test
    void changesVersionStatusToInactive() throws Exception {
        var created = mockMvc.perform(post("/api/systems/{systemId}/versions", systemId)
                        .contentType("application/json")
                        .content("{\"name\":\"V3.0\"}"))
                .andReturn();
        var versionId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(patch("/api/system-versions/{id}/status", versionId)
                        .contentType("application/json")
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void rejectsDeletingVersionWithActiveRequirements() throws Exception {
        var created = mockMvc.perform(post("/api/systems/{systemId}/versions", systemId)
                        .contentType("application/json")
                        .content("{\"name\":\"V4.0\"}"))
                .andReturn();
        var versionId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();
        mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"王强","department":"技术部","title":"版本关联需求","type":"BUG","content":"存在关联需求时禁止删除版本","systemId":%d,"targetVersionId":%s}
                                """.formatted(systemId, versionId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/system-versions/{id}", versionId))
                .andExpect(status().isConflict());
    }

    @Test
    void deletesVersionWithoutActiveRequirements() throws Exception {
        var created = mockMvc.perform(post("/api/systems/{systemId}/versions", systemId)
                        .contentType("application/json")
                        .content("{\"name\":\"V5.0\"}"))
                .andReturn();
        var versionId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(delete("/api/system-versions/{id}", versionId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/systems/{systemId}/versions", systemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + versionId + ")]").isEmpty());
    }
}
