package com.dth.frw.module.school.student.api.dto;

import java.util.List;

/**
 * {@code GET /students/structure}: the khối lớp and lớp of a year with how many
 * students each holds — the tree beside the student list. {@code total} counts
 * every student in the year and {@code unassigned} those not yet in a class.
 */
public record StudentStructureResponse(
        long total,
        long unassigned,
        List<Group> groups) {

    public record Group(Long id, String name, long studentCount, List<Klass> classes) {
    }

    public record Klass(Long id, String name, long studentCount) {
    }
}
