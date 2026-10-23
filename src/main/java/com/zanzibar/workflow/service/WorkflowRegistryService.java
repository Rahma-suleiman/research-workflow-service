package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.request.CreateRegistryEntryRequest;
import com.zanzibar.workflow.dto.request.UpdateRegistryEntryRequest;
import com.zanzibar.workflow.dto.response.RegistryEntryResponse;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.model.RegistryKey;
import com.zanzibar.workflow.model.WorkflowRole;
import com.zanzibar.workflow.model.WorkflowSubjectType;
import com.zanzibar.workflow.repository.WorkflowRoleRepository;
import com.zanzibar.workflow.repository.WorkflowSubjectTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Project-specific vocabulary: the roles users can hold and the subject types
 * workflows can govern. Each tenant registers its own values here, so the
 * engine code contains no domain-specific lists.
 */
@Service
public class WorkflowRegistryService {

    /** Built-in role for SYSTEM and EXTERNAL_EVENT transitions; never stored. */
    public static final String SYSTEM_ROLE = "SYSTEM";

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

    private final WorkflowRoleRepository roleRepository;
    private final WorkflowSubjectTypeRepository subjectTypeRepository;

    public WorkflowRegistryService(
        WorkflowRoleRepository roleRepository,
        WorkflowSubjectTypeRepository subjectTypeRepository
    ) {
        this.roleRepository = roleRepository;
        this.subjectTypeRepository = subjectTypeRepository;
    }

    /** Active registered roles plus the built-in SYSTEM role. */
    @Transactional(readOnly = true)
    public Set<String> activeRoleKeys(String tenantKey) {
        Set<String> keys = new LinkedHashSet<>();
        roleRepository.findByTenantKeyAndActiveTrueOrderByKeyAsc(tenantKey)
            .forEach(role -> keys.add(role.getKey()));
        keys.add(SYSTEM_ROLE);
        return keys;
    }

    @Transactional(readOnly = true)
    public Set<String> activeSubjectTypeKeys(String tenantKey) {
        Set<String> keys = new LinkedHashSet<>();
        subjectTypeRepository.findByTenantKeyAndActiveTrueOrderByKeyAsc(tenantKey)
            .forEach(type -> keys.add(type.getKey()));
        return keys;
    }

    @Transactional(readOnly = true)
    public List<RegistryEntryResponse> listRoles(String tenantKey) {
        return roleRepository.findByTenantKeyOrderByKeyAsc(tenantKey).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public RegistryEntryResponse createRole(
        String tenantKey,
        CreateRegistryEntryRequest request
    ) {
        String key = normalizeKey(request.key());
        if (SYSTEM_ROLE.equals(key)) {
            throw new WorkflowException(
                "SYSTEM is a built-in role and cannot be registered.");
        }
        if (roleRepository.existsById(new RegistryKey(tenantKey, key))) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "Role already exists: " + key);
        }
        WorkflowRole role = new WorkflowRole();
        role.setTenantKey(tenantKey);
        role.setKey(key);
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setActive(true);
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    public RegistryEntryResponse updateRole(
        String tenantKey,
        String key,
        UpdateRegistryEntryRequest request
    ) {
        String normalized = normalizeKey(key);
        WorkflowRole role = roleRepository.findById(new RegistryKey(tenantKey, normalized))
            .orElseThrow(() -> new WorkflowException(
                HttpStatus.NOT_FOUND, "Role not found: " + normalized));
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setActive(request.active());
        return toResponse(roleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public List<RegistryEntryResponse> listSubjectTypes(String tenantKey) {
        return subjectTypeRepository.findByTenantKeyOrderByKeyAsc(tenantKey).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public RegistryEntryResponse createSubjectType(
        String tenantKey,
        CreateRegistryEntryRequest request
    ) {
        String key = normalizeKey(request.key());
        if (subjectTypeRepository.existsById(new RegistryKey(tenantKey, key))) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "Subject type already exists: " + key);
        }
        WorkflowSubjectType type = new WorkflowSubjectType();
        type.setTenantKey(tenantKey);
        type.setKey(key);
        type.setName(request.name().trim());
        type.setDescription(request.description());
        type.setActive(true);
        return toResponse(subjectTypeRepository.save(type));
    }

    @Transactional
    public RegistryEntryResponse updateSubjectType(
        String tenantKey,
        String key,
        UpdateRegistryEntryRequest request
    ) {
        String normalized = normalizeKey(key);
        WorkflowSubjectType type = subjectTypeRepository
            .findById(new RegistryKey(tenantKey, normalized))
            .orElseThrow(() -> new WorkflowException(
                HttpStatus.NOT_FOUND, "Subject type not found: " + normalized));
        type.setName(request.name().trim());
        type.setDescription(request.description());
        type.setActive(request.active());
        return toResponse(subjectTypeRepository.save(type));
    }

    private String normalizeKey(String key) {
        String normalized = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new WorkflowException(
                "Invalid key. Use letters, numbers and underscores; "
                    + "the first character must be a letter.");
        }
        return normalized;
    }

    private RegistryEntryResponse toResponse(WorkflowRole role) {
        return new RegistryEntryResponse(role.getKey(), role.getName(),
            role.getDescription(), role.isActive(), role.getCreatedAt(), role.getUpdatedAt());
    }

    private RegistryEntryResponse toResponse(WorkflowSubjectType type) {
        return new RegistryEntryResponse(type.getKey(), type.getName(),
            type.getDescription(), type.isActive(), type.getCreatedAt(), type.getUpdatedAt());
    }
}
