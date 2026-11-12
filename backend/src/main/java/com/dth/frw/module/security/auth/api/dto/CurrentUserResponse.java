package com.dth.frw.module.security.auth.api.dto;

import com.dth.frw.domain.security.user.User;

/**
 * The signed-in identity, as {@code GET /auth/me} and every token response
 * report it.
 *
 * <p>Identity only: who the account is, not what it may do. Roles and
 * permissions are a separate payload behind a separate endpoint
 * ({@link AuthoritiesResponse}, {@code GET /auth/permissions}), for the same
 * reason they are no longer stamped into the access token — they grow with the
 * permission model, and the things that read an identity (an account menu, a
 * profile header) do not need them.
 *
 * <p>Narrower than the user resource's own response and declared separately
 * rather than imported: this is the shape of "who am I", and widening what an
 * administrator can see about somebody else should not widen it.
 */
public record CurrentUserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        /** The school the account works for, or null for one placed in none. */
        SchoolSummary organization) {

    /**
     * The school as an identity reports it. A school is an organization on the
     * API; this is what lets the shell show its name and decide whether the
     * school-year selector applies, without a second request.
     */
    public record SchoolSummary(Long id, String code, String name, String logo) {
    }

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getOrganization() == null
                        ? null
                        : new SchoolSummary(
                                user.getOrganization().getId(),
                                user.getOrganization().getCode(),
                                user.getOrganization().getName(),
                                user.getOrganization().getLogo()));
    }
}
