package com.dth.frw.domain.school.fee;

/**
 * What a khoản thu is. Stored and returned, but not edited on the screen: the
 * Jmix form never offered it either, so every khoản thu is {@code REGULAR}.
 */
public enum FeeNature {
    REGULAR,
    COLLECT_ON_BEHALF
}
