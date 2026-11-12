package com.dth.frw.domain.school.fee;

/** Who registered the student for a khoản thu. Only the school does, for now. */
public enum FeeRegistrationSource {
    SCHOOL,
    /** Reserved for a parent-facing screen that does not exist yet. */
    PARENT
}
