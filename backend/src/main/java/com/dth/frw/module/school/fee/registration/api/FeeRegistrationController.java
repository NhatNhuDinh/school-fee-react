package com.dth.frw.module.school.fee.registration.api;

import com.dth.frw.module.school.fee.registration.api.dto.FeeRegistrationMatrixResponse;
import com.dth.frw.module.school.fee.registration.api.dto.FeeRegistrationSaveRequest;
import com.dth.frw.module.school.fee.registration.service.FeeRegistrationService;
import com.dth.frw.shared.security.Permissions;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/fee_registrations} — the {@code fee_registrations} resource. The path
 * segment is the resource name verbatim, and the first half of the permission codes
 * below.
 *
 * <p>Not the usual list-and-record shape: the screen is a grid for one lớp, so the API
 * answers a whole matrix and takes a whole matrix back. There is no endpoint for a
 * single registration, and no delete — unticking a cell is the unregistration.
 */
@RestController
@RequestMapping("/api/fee_registrations")
public class FeeRegistrationController {

    private final FeeRegistrationService service;

    public FeeRegistrationController(FeeRegistrationService service) {
        this.service = service;
    }

    @GetMapping("/matrix")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_REGISTRATIONS_READ + "')")
    public FeeRegistrationMatrixResponse matrix(@RequestParam("schoolClassId") Long schoolClassId) {
        return service.matrix(schoolClassId);
    }

    @PutMapping("/class/{schoolClassId}")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_REGISTRATIONS_WRITE + "')")
    public FeeRegistrationService.Result save(
            @PathVariable Long schoolClassId, @Valid @RequestBody FeeRegistrationSaveRequest request) {
        return service.save(schoolClassId, request);
    }
}
