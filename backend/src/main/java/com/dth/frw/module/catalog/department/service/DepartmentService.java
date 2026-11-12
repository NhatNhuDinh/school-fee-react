package com.dth.frw.module.catalog.department.service;

import com.dth.frw.domain.catalog.department.Department;
import com.dth.frw.module.catalog.department.repo.DepartmentRepository;
import com.dth.frw.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What is left of departments: resolving one by id, for the user module.
 *
 * <p>Departments are retired from the product -- no screen, no endpoint, no
 * permission -- but a user's placement still carries an optional department,
 * checked by the composite foreign key on {@code users}. That column is part of
 * the account and row-level model and is removed deliberately or not at all, so
 * this slice stays until it is.
 */
@Service
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository repository;

    public DepartmentService(DepartmentRepository repository) {
        this.repository = repository;
    }

    public Department get(Long id) {
        return repository.findOne((root, query, cb) -> cb.equal(root.get("id"), id))
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }
}
