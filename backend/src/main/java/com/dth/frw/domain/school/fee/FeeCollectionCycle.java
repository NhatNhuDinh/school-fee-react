package com.dth.frw.domain.school.fee;

/** How often a khoản thu is collected, and so how many collection dates it has. */
public enum FeeCollectionCycle {
    /** Every month; no dates to choose. */
    MONTHLY,
    /** Four times a year, one month in each quarter. */
    QUARTERLY,
    /** Once per academic period (học kỳ), a month inside each. */
    SEMESTER,
    /** Once a year, in a month of the year. */
    ACADEMIC_YEAR
}
