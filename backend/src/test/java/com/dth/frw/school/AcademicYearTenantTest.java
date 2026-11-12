package com.dth.frw.school;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * The tenant boundary, end to end: real login, real filters, real database.
 *
 * <p>Runs against the two development schools seeded by changeset
 * {@code 019}. Each test rolls back, so nothing it creates survives.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AcademicYearTenantTest {

    private static final String PASSWORD = "Admin@123";

    @Autowired
    private MockMvc mvc;

    @Test
    void aSchoolSeesOnlyItsOwnYears() throws Exception {
        mvc.perform(get("/api/academic_years").header("Authorization", bearer("nbk.ketoan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].organization.code", everyItem(equalTo("NBK"))));

        mvc.perform(get("/api/academic_years").header("Authorization", bearer("cg.ketoan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].organization.code", everyItem(equalTo("CG"))));
    }

    @Test
    void theSystemAdministratorSeesEverySchool() throws Exception {
        mvc.perform(get("/api/academic_years").header("Authorization", bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].organization.code", hasItems("NBK", "CG")));
    }

    @Test
    void anotherSchoolsYearIsNotFoundRatherThanForbidden() throws Exception {
        long cgYear = yearIdOf("CG");

        mvc.perform(get("/api/academic_years/" + cgYear).header("Authorization", bearer("nbk.ketoan")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aSchoolCannotWriteIntoAnotherSchoolWhichDoesNotExistForIt() throws Exception {
        long cg = organizationIdOf("CG");

        mvc.perform(post("/api/academic_years")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organization": {"id": %d},
                                 "startDate": "2026-09-05", "endDate": "2027-05-27"}
                                """.formatted(cg)))
                // The other school is not merely off limits: it is not there. Its
                // organization record is filtered out, so it cannot even be named.
                .andExpect(status().isNotFound());
    }

    @Test
    void aYearCreatedWithoutASchoolBelongsToTheCallersSchool() throws Exception {
        mvc.perform(post("/api/academic_years")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate": "2028-09-05", "endDate": "2029-05-27",
                                 "periods": [
                                   {"name": "Học kỳ 1", "startDate": "2028-09-05", "endDate": "2029-01-17"},
                                   {"name": "Học kỳ 2", "startDate": "2029-01-18", "endDate": "2029-05-27"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organization.code", is("NBK")))
                .andExpect(jsonPath("$.name", is("2028-2029")))
                .andExpect(jsonPath("$.periods", hasSize(2)));
    }

    @Test
    void aYearMustEndAfterItStartsAndItsTermsMustNotOverlap() throws Exception {
        mvc.perform(post("/api/academic_years")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate": "2027-05-27", "endDate": "2026-09-05"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.endDate").exists());

        mvc.perform(post("/api/academic_years")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate": "2026-09-05", "endDate": "2027-05-27",
                                 "periods": [
                                   {"name": "A", "startDate": "2026-09-05", "endDate": "2027-01-20"},
                                   {"name": "B", "startDate": "2027-01-10", "endDate": "2027-05-27"}]}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.periods").exists());
    }

    @Test
    void aSchoolCannotHaveTheSameYearTwice() throws Exception {
        mvc.perform(post("/api/academic_years")
                        .header("Authorization", bearer("nbk.ketoan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate": "2025-09-05", "endDate": "2026-05-27"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void aSchoolsStaffCannotListSchools() throws Exception {
        mvc.perform(get("/api/organizations").header("Authorization", bearer("nbk.ketoan")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAccountThatMayListSchoolsStillSeesOnlyItsOwn() throws Exception {
        // An account holding organizations:read -- the plain USER role -- placed in
        // one school. The grant is what lets the request in; the school filter is
        // what narrows what comes back.
        long nbk = organizationIdOf("NBK");
        mvc.perform(post("/api/users")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "reader.nbk", "email": "reader@nbk.local",
                                 "fullName": "Reader", "password": "Reader@12345",
                                 "organization": {"id": %d}, "roles": [{"id": %d}]}
                                """.formatted(nbk, roleIdOf("USER"))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/organizations").header("Authorization", bearer("reader.nbk", "Reader@12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code", is("NBK")));

        mvc.perform(get("/api/organizations").header("Authorization", bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItems("NBK", "CG")));
    }

    // ---- helpers

    private String bearer(String username) throws Exception {
        return bearer(username, PASSWORD);
    }

    private String bearer(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private long yearIdOf(String schoolCode) throws Exception {
        String body = mvc.perform(get("/api/academic_years").header("Authorization", bearer("admin")))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Number> ids = JsonPath.read(
                body, "$[?(@.organization.code == '%s')].id".formatted(schoolCode));
        return ids.get(0).longValue();
    }

    private long roleIdOf(String code) throws Exception {
        String body = mvc.perform(get("/api/roles").header("Authorization", bearer("admin")))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Number> ids = JsonPath.read(body, "$[?(@.code == '%s')].id".formatted(code));
        return ids.get(0).longValue();
    }

    private long organizationIdOf(String code) throws Exception {
        String body = mvc.perform(get("/api/organizations").header("Authorization", bearer("admin")))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Number> ids = JsonPath.read(body, "$[?(@.code == '%s')].id".formatted(code));
        return ids.get(0).longValue();
    }
}
