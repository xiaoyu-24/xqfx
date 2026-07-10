package com.xqfx.requirements.system;

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
        "spring.datasource.url=jdbc:h2:mem:requirements;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SystemApiTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    void createsSystemWithOwnerAndCollaborators() throws Exception {
        mockMvc.perform(post("/api/systems")
                        .contentType("application/json")
                        .content("""
                                {"name":"客户管理系统","ownerName":"李明","collaborators":["王芳","赵敏"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("客户管理系统"))
                .andExpect(jsonPath("$.ownerName").value("李明"))
                .andExpect(jsonPath("$.collaborators[0]").value("王芳"));
    }

    @Test
    void listsCreatedSystems() throws Exception {
        mockMvc.perform(post("/api/systems")
                .contentType("application/json")
                .content("""
                        {"name":"办公系统","ownerName":"陈晨","collaborators":[]}
                        """));

        mockMvc.perform(get("/api/systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '办公系统')]").exists());
    }

    @Test
    void updatesSystemNameOwnerAndCollaborators() throws Exception {
        var created = mockMvc.perform(post("/api/systems")
                        .contentType("application/json")
                        .content("""
                                {"name":"旧系统名","ownerName":"旧负责人","collaborators":["协助人甲"]}
                                """))
                .andReturn();
        var id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(put("/api/systems/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {"name":"新系统名","ownerName":"新负责人","collaborators":["协助人乙","协助人丙"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新系统名"))
                .andExpect(jsonPath("$.ownerName").value("新负责人"))
                .andExpect(jsonPath("$.collaborators[0]").value("协助人乙"))
                .andExpect(jsonPath("$.collaborators[1]").value("协助人丙"));
    }

    @Test
    void changesSystemStatusToInactive() throws Exception {
        var created = mockMvc.perform(post("/api/systems")
                        .contentType("application/json")
                        .content("""
                                {"name":"待停用系统","ownerName":"负责人","collaborators":[]}
                                """))
                .andReturn();
        var id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(patch("/api/systems/{id}/status", id)
                        .contentType("application/json")
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void rejectsDeletingSystemWithActiveRequirements() throws Exception {
        var created = mockMvc.perform(post("/api/systems")
                        .contentType("application/json")
                        .content("""
                                {"name":"存在需求的系统","ownerName":"负责人","collaborators":[]}
                                """))
                .andReturn();
        var id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();
        mockMvc.perform(post("/api/requirements")
                        .contentType("application/json")
                        .content("""
                                {"requesterName":"林琳","department":"信息部","title":"关联需求","type":"REQUIREMENT","content":"系统存在关联需求时禁止删除","systemId":%s}
                                """.formatted(id)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/systems/{id}", id))
                .andExpect(status().isConflict());
    }

    @Test
    void deletesSystemWithoutActiveRequirements() throws Exception {
        var created = mockMvc.perform(post("/api/systems")
                        .contentType("application/json")
                        .content("""
                                {"name":"可删除系统","ownerName":"负责人","collaborators":[]}
                                """))
                .andReturn();
        var id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(delete("/api/systems/{id}", id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
    }

    @Test
    void rejectsDuplicateSystemNameIgnoringCaseAndWhitespace() throws Exception {
        mockMvc.perform(post("/api/systems")
                        .contentType("application/json")
                        .content("""
                                {"name":"客户服务系统","ownerName":"负责人甲","collaborators":[]}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/systems")
                        .contentType("application/json")
                        .content("""
                                {"name":"  客户服务系统  ","ownerName":"负责人乙","collaborators":[]}
                                """))
                .andExpect(status().isConflict());
    }
}
