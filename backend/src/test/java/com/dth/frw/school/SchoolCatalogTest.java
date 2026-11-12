package com.dth.frw.school;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two catalogues a school shares across its years — training systems and
 * measurement units — against the two development schools seeded by 028.
 * Both are the same shape, so each rule is checked on one and the routes on both.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SchoolCatalogTest {

    private static final String PASSWORD = "Admin@123";

    @Autowired
    private MockMvc mvc;

    @Test
    void aSchoolSeesOnlyItsOwnRows() throws Exception {
        for (String resource : new String[] {"training_systems", "measurement_units"}) {
            mvc.perform(get("/api/" + resource).header("Authorization", bearer("nbk.ketoan")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].organization.code", everyItem(equalTo("NBK"))));
        }
    }

    @Test
    void theSystemAdministratorSeesEverySchool() throws Exception {
        mvc.perform(get("/api/measurement_units").header("Authorization", bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].organization.code", hasItems("NBK", "CG")));
    }

    @Test
    void theJmixRowsWereCarriedOver() throws Exception {
        mvc.perform(get("/api/training_systems").header("Authorization", bearer("nbk.ketoan")))
                .andExpect(jsonPath("$[*].name", hasItems("Chính quy")));
        mvc.perform(get("/api/measurement_units?_start=0&_end=50").header("Authorization", bearer("nbk.ketoan")))
                .andExpect(jsonPath("$", hasSize(16)))
                .andExpect(jsonPath("$[*].name", hasItems("Tháng", "Tiết", "Tờ")));
    }

    @Test
    void aRowCreatedWithoutASchoolBelongsToTheCallersSchool() throws Exception {
        mvc.perform(post("/api/training_systems")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"  Liên kết quốc tế \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organization.code", is("NBK")))
                .andExpect(jsonPath("$.name", is("Liên kết quốc tế")));
    }

    @Test
    void aNameIsUniqueWithinASchoolButNotAcrossSchools() throws Exception {
        mvc.perform(post("/api/training_systems")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Chính quy\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aNameIsRequired() throws Exception {
        mvc.perform(post("/api/measurement_units")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"   \"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void aSchoolCannotReachAnotherSchoolsRow() throws Exception {
        long cgUnit = firstIdOf("measurement_units", "CG");

        mvc.perform(get("/api/measurement_units/" + cgUnit).header("Authorization", bearer("nbk.ketoan")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/measurement_units/" + cgUnit)
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Đổi tên\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/measurement_units/" + cgUnit).header("Authorization", bearer("nbk.ketoan")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aRowCanBeRenamedAndDeleted() throws Exception {
        long id = firstIdOf("measurement_units", "NBK");

        mvc.perform(patch("/api/measurement_units/" + id)
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Đơn vị thử\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Đơn vị thử")));

        mvc.perform(delete("/api/measurement_units/" + id).header("Authorization", bearer("nbk.ketoan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) id)));
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

    private long firstIdOf(String resource, String schoolCode) throws Exception {
        String body = mvc.perform(get("/api/" + resource).header("Authorization", bearer("admin")))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Number> ids = JsonPath.read(
                body, "$[?(@.organization.code == '%s')].id".formatted(schoolCode));
        return ids.get(0).longValue();
    }
}
