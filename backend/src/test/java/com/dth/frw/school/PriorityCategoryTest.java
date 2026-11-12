package com.dth.frw.school;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
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

/** The diện ưu tiên of a school year, against the two development schools. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PriorityCategoryTest {

    private static final String PASSWORD = "Admin@123";

    @Autowired
    private MockMvc mvc;

    @Test
    void aCategoryBelongsToTheYearAndSchoolOfItsYear() throws Exception {
        long year = yearOf("nbk.ketoan", null);

        mvc.perform(post("/api/priority_categories")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(year, "  Con thương binh ", "PERCENT", "50", "FULL_YEAR", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Con thương binh")))
                .andExpect(jsonPath("$.organization.code", is("NBK")))
                .andExpect(jsonPath("$.discountType", is("PERCENT")))
                .andExpect(jsonPath("$.discountValue", is(50)))
                .andExpect(jsonPath("$.applicationPeriod", is("FULL_YEAR")));
    }

    @Test
    void theListNarrowsToOneYear() throws Exception {
        long year = yearOf("nbk.ketoan", null);
        create("nbk.ketoan", year, "Hộ nghèo", "AMOUNT", "100000", "SEMESTER_ONE", null, null);

        mvc.perform(get("/api/priority_categories?academicYear.id=" + year).header("Authorization", bearer("nbk.ketoan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].academicYear.id", everyItem(equalTo((int) year))))
                .andExpect(jsonPath("$[*].name", hasItem("Hộ nghèo")));
    }

    @Test
    void aNameIsUniqueWithinAYear() throws Exception {
        long year = yearOf("nbk.ketoan", null);
        create("nbk.ketoan", year, "Mồ côi", "PERCENT", "100", "FULL_YEAR", null, null);

        mvc.perform(post("/api/priority_categories")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(year, "Mồ côi", "PERCENT", "10", "FULL_YEAR", null, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void aPercentageCannotPassOneHundred() throws Exception {
        mvc.perform(post("/api/priority_categories")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(yearOf("nbk.ketoan", null), "Quá mức", "PERCENT", "120", "FULL_YEAR", null, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.discountValue").exists());
    }

    @Test
    void aCustomPeriodNeedsBothMonthsInOrder() throws Exception {
        long year = yearOf("nbk.ketoan", null);

        mvc.perform(post("/api/priority_categories")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(year, "Thiếu tháng", "PERCENT", "10", "CUSTOM", 9, null)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/priority_categories")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(year, "Sai thứ tự", "PERCENT", "10", "CUSTOM", 9, 5)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/priority_categories")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(year, "Đủ tháng", "PERCENT", "10", "CUSTOM", 9, 12)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromMonth", is(9)))
                .andExpect(jsonPath("$.toMonth", is(12)));
    }

    @Test
    void monthsAreDroppedWhenThePeriodIsNotCustom() throws Exception {
        long year = yearOf("nbk.ketoan", null);
        long id = create("nbk.ketoan", year, "Theo kỳ", "PERCENT", "10", "CUSTOM", 2, 4);

        mvc.perform(patch("/api/priority_categories/" + id)
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationPeriod\": \"SEMESTER_TWO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationPeriod", is("SEMESTER_TWO")))
                .andExpect(jsonPath("$.fromMonth", nullValue()))
                .andExpect(jsonPath("$.toMonth", nullValue()));
    }

    @Test
    void aSchoolCannotReachAnotherSchoolsCategory() throws Exception {
        long cgYear = yearOf("admin", "CG");
        long cgRow = create("admin", cgYear, "Riêng của CG", "PERCENT", "10", "FULL_YEAR", null, null);

        mvc.perform(post("/api/priority_categories")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(cgYear, "Lạ", "PERCENT", "10", "FULL_YEAR", null, null)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/priority_categories/" + cgRow).header("Authorization", bearer("nbk.ketoan")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/priority_categories/" + cgRow).header("Authorization", bearer("nbk.ketoan")))
                .andExpect(status().isNotFound());
    }

    // ---- helpers

    private String body(long year, String name, String type, String value, String period, Integer from, Integer to) {
        return "{\"academicYear\": {\"id\": %d}, \"name\": \"%s\", \"discountType\": \"%s\", \"discountValue\": %s, "
                .formatted(year, name, type, value)
                + "\"applicationPeriod\": \"%s\"%s%s}".formatted(
                        period,
                        from == null ? "" : ", \"fromMonth\": " + from,
                        to == null ? "" : ", \"toMonth\": " + to);
    }

    private String bearer(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.accessToken");
    }

    /** The first year the caller can see, optionally of one school (for the administrator). */
    private long yearOf(String username, String schoolCode) throws Exception {
        String response = mvc.perform(get("/api/academic_years").header("Authorization", bearer(username)))
                .andReturn().getResponse().getContentAsString();
        String path = schoolCode == null ? "$[*].id" : "$[?(@.organization.code == '%s')].id".formatted(schoolCode);
        List<Number> ids = JsonPath.read(response, path);
        return ids.get(0).longValue();
    }

    private long create(String username, long year, String name, String type, String value, String period,
            Integer from, Integer to) throws Exception {
        String response = mvc.perform(post("/api/priority_categories")
                        .header("Authorization", bearer(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(year, name, type, value, period, from, to)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }
}
