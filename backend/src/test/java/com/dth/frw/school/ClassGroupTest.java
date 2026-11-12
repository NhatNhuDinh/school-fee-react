package com.dth.frw.school;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The khối lớp of a school year and the lớp inside each, against the two
 * development schools seeded by 026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClassGroupTest {

    private static final String PASSWORD = "Admin@123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void aGroupBelongsToTheYearAndSchoolOfItsYear() throws Exception {
        long year = yearOf("nbk.ketoan");

        mvc.perform(post("/api/class_groups")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicYear\": {\"id\": %d}, \"name\": \"  Khối 10 \"}".formatted(year)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Khối 10")))
                .andExpect(jsonPath("$.organization.code", is("NBK")))
                .andExpect(jsonPath("$.academicYear.id", is((int) year)));
    }

    @Test
    void theListNarrowsToOneYear() throws Exception {
        long year = yearOf("nbk.ketoan");
        createGroup("nbk.ketoan", year, "Khối 11");

        mvc.perform(get("/api/class_groups?academicYear.id=" + year).header("Authorization", bearer("nbk.ketoan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].academicYear.id", everyItem(equalTo((int) year))))
                .andExpect(jsonPath("$[*].name", org.hamcrest.Matchers.hasItem("Khối 11")));
    }

    @Test
    void aNameIsUniqueWithinAYear() throws Exception {
        long year = yearOf("nbk.ketoan");
        createGroup("nbk.ketoan", year, "Khối 12");

        mvc.perform(post("/api/class_groups")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicYear\": {\"id\": %d}, \"name\": \"Khối 12\"}".formatted(year)))
                .andExpect(status().isConflict());
    }

    @Test
    void aYearAndANameAreRequired() throws Exception {
        mvc.perform(post("/api/class_groups")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Khối 10\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/class_groups")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicYear\": {\"id\": %d}, \"name\": \"  \"}".formatted(yearOf("nbk.ketoan"))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void aSchoolCannotReachAnotherSchoolsYearOrGroup() throws Exception {
        long cgYear = yearOf("admin", "CG");
        long cgGroup = createGroup("admin", cgYear, "Khối riêng");

        mvc.perform(post("/api/class_groups")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicYear\": {\"id\": %d}, \"name\": \"Khối lạ\"}".formatted(cgYear)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/class_groups/" + cgGroup).header("Authorization", bearer("nbk.ketoan")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/class_groups/" + cgGroup).header("Authorization", bearer("nbk.ketoan")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aGroupCanBeRenamed() throws Exception {
        long group = createGroup("nbk.ketoan", yearOf("nbk.ketoan"), "Khối 9");

        mvc.perform(patch("/api/class_groups/" + group)
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Khối 8\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Khối 8")));
    }

    @Test
    void classesBelongToTheirGroupAndGoWithIt() throws Exception {
        long group = createGroup("nbk.ketoan", yearOf("nbk.ketoan"), "Khối 10");

        mvc.perform(post("/api/school_classes")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classGroup\": {\"id\": %d}, \"name\": \"10A1\"}".formatted(group)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.classGroup.id", is((int) group)))
                .andExpect(jsonPath("$.organization.code", is("NBK")));
        // The same name twice in one group is refused.
        mvc.perform(post("/api/school_classes")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classGroup\": {\"id\": %d}, \"name\": \"10A1\"}".formatted(group)))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/school_classes?classGroup.id=" + group).header("Authorization", bearer("nbk.ketoan")))
                .andExpect(jsonPath("$", hasSize(1)));

        // The test is one transaction, so the class just created is still managed and
        // would be flushed against the deleted group; a real request has its own.
        entityManager.flush();
        entityManager.clear();
        mvc.perform(delete("/api/class_groups/" + group).header("Authorization", bearer("nbk.ketoan")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/school_classes?classGroup.id=" + group).header("Authorization", bearer("nbk.ketoan")))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- helpers

    private String bearer(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private long yearOf(String username) throws Exception {
        return yearOf(username, null);
    }

    /** The first year the caller can see, optionally of one school (for the administrator). */
    private long yearOf(String username, String schoolCode) throws Exception {
        String body = mvc.perform(get("/api/academic_years").header("Authorization", bearer(username)))
                .andReturn().getResponse().getContentAsString();
        String path = schoolCode == null ? "$[*].id" : "$[?(@.organization.code == '%s')].id".formatted(schoolCode);
        List<Number> ids = JsonPath.read(body, path);
        return ids.get(0).longValue();
    }

    private long createGroup(String username, long year, String name) throws Exception {
        String body = mvc.perform(post("/api/class_groups")
                        .header("Authorization", bearer(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicYear\": {\"id\": %d}, \"name\": \"%s\"}".formatted(year, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
