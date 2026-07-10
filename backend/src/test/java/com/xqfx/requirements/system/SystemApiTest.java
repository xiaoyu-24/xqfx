package com.xqfx.requirements.system;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
