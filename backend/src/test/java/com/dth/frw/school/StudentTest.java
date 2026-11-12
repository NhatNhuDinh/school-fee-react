package com.dth.frw.school;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
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

/** Students in a school year: the enrollment, the class tree, assigning and importing. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentTest {

    private static final String PASSWORD = "Admin@123";
    private static final String NBK = "nbk.ketoan";

    @Autowired
    private MockMvc mvc;

    @Test
    void aStudentGetsACodeWhenNoneIsGiven() throws Exception {
        long year = yearOf(NBK, null);
        long klass = classIn(NBK, year, "Khối 10", "10A1");

        mvc.perform(post("/api/students")
                        .header("Authorization", bearer(NBK))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(year, klass, "  Nguyễn Văn A ", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.student.fullName", is("Nguyễn Văn A")))
                .andExpect(jsonPath("$.student.studentCode", startsWith("NBK")))
                .andExpect(jsonPath("$.schoolClass.name", is("10A1")))
                .andExpect(jsonPath("$.schoolClass.classGroup.name", is("Khối 10")))
                .andExpect(jsonPath("$.academicYear.id", is((int) year)));
    }

    @Test
    void aStudentCodeIsUniqueWithinTheSchool() throws Exception {
        long year = yearOf(NBK, null);
        long klass = classIn(NBK, year, "Khối 10", "10A1");
        create(NBK, year, klass, "Học sinh một", "HS-001");

        mvc.perform(post("/api/students")
                        .header("Authorization", bearer(NBK))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(year, klass, "Học sinh hai", "HS-001")))
                .andExpect(status().isConflict());
    }

    @Test
    void aNameIsRequired() throws Exception {
        long year = yearOf(NBK, null);
        long klass = classIn(NBK, year, "Khối 10", "10A1");

        mvc.perform(post("/api/students")
                        .header("Authorization", bearer(NBK))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(year, klass, "   ", null)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void aClassOfAnotherYearIsRefused() throws Exception {
        long year = yearOf(NBK, null);
        long later = newYear(NBK, "2030-09-05", "2031-05-27");
        long klass = classIn(NBK, later, "Khối 10", "10A1");

        mvc.perform(post("/api/students")
                        .header("Authorization", bearer(NBK))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(year, klass, "Lạc lớp", null)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void theListNarrowsByClassGroupAndByNoClass() throws Exception {
        long year = yearOf(NBK, null);
        long klass = classIn(NBK, year, "Khối 10", "10A1");
        long group = groupOfClass(klass);
        create(NBK, year, klass, "Có lớp", null);

        mvc.perform(get("/api/students?academicYear.id=%d&classGroup.id=%d".formatted(year, group))
                        .header("Authorization", bearer(NBK)))
                .andExpect(jsonPath("$[?(@.student.fullName == 'Có lớp')]", hasSize(1)));
        mvc.perform(get("/api/students?academicYear.id=%d&unassigned=true".formatted(year))
                        .header("Authorization", bearer(NBK)))
                .andExpect(jsonPath("$[?(@.student.fullName == 'Có lớp')]", hasSize(0)));
    }

    @Test
    void anOptionalFieldCanBeClearedAndTheRestIsKept() throws Exception {
        long year = yearOf(NBK, null);
        long klass = classIn(NBK, year, "Khối 10", "10A1");
        long id = createWith(NBK, year, klass,
                "{\"fullName\": \"Sửa thử\", \"guardianName\": \"Cha\", \"guardianPhone\": \"0900000000\"}");

        mvc.perform(patch("/api/students/" + id)
                        .header("Authorization", bearer(NBK))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guardianName\": \"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.guardianName", nullValue()))
                .andExpect(jsonPath("$.student.guardianPhone", is("0900000000")))
                .andExpect(jsonPath("$.student.fullName", is("Sửa thử")));
    }

    @Test
    void theTreeCountsStudentsPerClass() throws Exception {
        long year = yearOf(NBK, null);
        long a = classIn(NBK, year, "Khối đếm", "10A1");
        long b = classIn(NBK, year, "Khối đếm", "10A2");
        create(NBK, year, a, "Một", null);
        create(NBK, year, a, "Hai", null);
        create(NBK, year, b, "Ba", null);

        mvc.perform(get("/api/students/structure?academicYearId=" + year).header("Authorization", bearer(NBK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[?(@.name == 'Khối đếm')].studentCount", org.hamcrest.Matchers.contains(3)))
                .andExpect(jsonPath("$.groups[?(@.name == 'Khối đếm')].classes[0].studentCount", org.hamcrest.Matchers.contains(2)));
    }

    @Test
    void studentsCanBeMovedIntoAnotherClass() throws Exception {
        long year = yearOf(NBK, null);
        long a = classIn(NBK, year, "Khối 10", "10A1");
        long b = classIn(NBK, year, "Khối 10", "10A2");
        long id = create(NBK, year, a, "Chuyển lớp", null);

        mvc.perform(post("/api/students/assign-class")
                        .header("Authorization", bearer(NBK))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": [%d], \"schoolClassId\": %d, \"transferInDate\": \"2026-10-01\"}"
                                .formatted(id, b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(1)));
        mvc.perform(get("/api/students/" + id).header("Authorization", bearer(NBK)))
                .andExpect(jsonPath("$.schoolClass.name", is("10A2")))
                .andExpect(jsonPath("$.transferInDate", is("2026-10-01")));
    }

    @Test
    void studentsAreCarriedIntoALaterYearWithoutAClass() throws Exception {
        long year = yearOf(NBK, null);
        long klass = classIn(NBK, year, "Khối 10", "10A1");
        long id = create(NBK, year, klass, "Lên lớp", null);
        long later = newYear(NBK, "2030-09-05", "2031-05-27");

        mvc.perform(post("/api/students/import")
                        .header("Authorization", bearer(NBK))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAcademicYearId\": %d, \"targetAcademicYearId\": %d, \"ids\": [%d]}"
                                .formatted(year, later, id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(1)));
        // Running it again changes nothing.
        mvc.perform(post("/api/students/import")
                        .header("Authorization", bearer(NBK))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAcademicYearId\": %d, \"targetAcademicYearId\": %d, \"ids\": [%d]}"
                                .formatted(year, later, id)))
                .andExpect(jsonPath("$.count", is(0)));

        mvc.perform(get("/api/students?academicYear.id=%d&unassigned=true".formatted(later))
                        .header("Authorization", bearer(NBK)))
                .andExpect(jsonPath("$[?(@.student.fullName == 'Lên lớp')]", hasSize(1)));
        // Already carried over, so no longer on offer.
        mvc.perform(get("/api/students?academicYear.id=%d&notEnrolledIn=%d".formatted(year, later))
                        .header("Authorization", bearer(NBK)))
                .andExpect(jsonPath("$[?(@.student.fullName == 'Lên lớp')]", hasSize(0)));
    }

    @Test
    void aSchoolCannotReachAnotherSchoolsStudent() throws Exception {
        long cgYear = yearOf("admin", "CG");
        long cgClass = classIn("admin", cgYear, "Khối riêng", "Lớp riêng");
        long cgStudent = create("admin", cgYear, cgClass, "Của CG", null);

        mvc.perform(get("/api/students/" + cgStudent).header("Authorization", bearer(NBK)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/students/" + cgStudent).header("Authorization", bearer(NBK)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingTheOnlyEnrollmentRemovesTheStudentToo() throws Exception {
        long year = yearOf(NBK, null);
        long klass = classIn(NBK, year, "Khối 10", "10A1");
        long id = create(NBK, year, klass, "Xóa hết", "HS-XOA");

        mvc.perform(delete("/api/students/" + id).header("Authorization", bearer(NBK)))
                .andExpect(status().isOk());
        // The code is free again, which it is only if the student went too.
        mvc.perform(post("/api/students")
                        .header("Authorization", bearer(NBK))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(year, klass, "Dùng lại mã", "HS-XOA")))
                .andExpect(status().isCreated());
    }

    // ---- helpers

    private String body(long year, long klass, String name, String code) {
        return "{\"academicYear\": {\"id\": %d}, \"schoolClass\": {\"id\": %d}, \"fullName\": \"%s\"%s}"
                .formatted(year, klass, name, code == null ? "" : ", \"studentCode\": \"" + code + "\"");
    }

    private String bearer(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.accessToken");
    }

    private long yearOf(String username, String schoolCode) throws Exception {
        String response = mvc.perform(get("/api/academic_years").header("Authorization", bearer(username)))
                .andReturn().getResponse().getContentAsString();
        String path = schoolCode == null ? "$[*].id" : "$[?(@.organization.code == '%s')].id".formatted(schoolCode);
        List<Number> ids = JsonPath.read(response, path);
        return ids.get(0).longValue();
    }

    private long newYear(String username, String start, String end) throws Exception {
        String response = mvc.perform(post("/api/academic_years")
                        .header("Authorization", bearer(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\": \"%s\", \"endDate\": \"%s\"}".formatted(start, end)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    /** A class in a (new) khối lớp of the year; returns the class id. */
    private long classIn(String username, long year, String groupName, String className) throws Exception {
        String group = mvc.perform(post("/api/class_groups")
                        .header("Authorization", bearer(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicYear\": {\"id\": %d}, \"name\": \"%s\"}".formatted(year, groupName)))
                .andReturn().getResponse().getContentAsString();
        long groupId;
        if (group.contains("\"id\"") && !group.contains("đã có")) {
            groupId = ((Number) JsonPath.read(group, "$.id")).longValue();
        } else {
            String list = mvc.perform(get("/api/class_groups?academicYear.id=" + year)
                            .header("Authorization", bearer(username)))
                    .andReturn().getResponse().getContentAsString();
            List<Number> ids = JsonPath.read(list, "$[?(@.name == '%s')].id".formatted(groupName));
            groupId = ids.get(0).longValue();
        }
        String response = mvc.perform(post("/api/school_classes")
                        .header("Authorization", bearer(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classGroup\": {\"id\": %d}, \"name\": \"%s\"}".formatted(groupId, className)))
                .andReturn().getResponse().getContentAsString();
        if (response.contains("\"id\"") && !response.contains("đã có")) {
            return ((Number) JsonPath.read(response, "$.id")).longValue();
        }
        String list = mvc.perform(get("/api/school_classes?classGroup.id=" + groupId)
                        .header("Authorization", bearer(username)))
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(list, "$[?(@.name == '%s')].id".formatted(className));
        return ids.get(0).longValue();
    }

    private long groupOfClass(long classId) throws Exception {
        String response = mvc.perform(get("/api/school_classes/" + classId).header("Authorization", bearer(NBK)))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.classGroup.id")).longValue();
    }

    private long create(String username, long year, long klass, String name, String code) throws Exception {
        String response = mvc.perform(post("/api/students")
                        .header("Authorization", bearer(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(year, klass, name, code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long createWith(String username, long year, long klass, String extraJson) throws Exception {
        String merged = "{\"academicYear\": {\"id\": %d}, \"schoolClass\": {\"id\": %d}, %s"
                .formatted(year, klass, extraJson.substring(1));
        String response = mvc.perform(post("/api/students")
                        .header("Authorization", bearer(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(merged))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }
}
