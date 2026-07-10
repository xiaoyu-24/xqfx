package com.xqfx.requirements.system;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:versions;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
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
                SystemProfile.create("客户管理系统", "李明", java.util.List.of())
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
}
