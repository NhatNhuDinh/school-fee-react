package com.dth.frw.domain.school.fee;

/** Who a khoản thu is collected from. */
public enum FeeScope {
    /** Every student of the school. */
    ALL_SCHOOL,
    /** The students of one khối lớp. */
    CLASS_GROUP,
    /** The students of one lớp. */
    CLASS,
    /** Students not yet placed in a class. */
    UNASSIGNED
}
