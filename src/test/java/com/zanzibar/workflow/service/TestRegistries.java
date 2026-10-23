package com.zanzibar.workflow.service;

import com.zanzibar.workflow.model.WorkflowRole;
import com.zanzibar.workflow.model.WorkflowSubjectType;
import com.zanzibar.workflow.repository.WorkflowRoleRepository;
import com.zanzibar.workflow.repository.WorkflowSubjectTypeRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Builds a registry backed by mocks with the given active roles and subject types for any tenant. */
final class TestRegistries {

    private TestRegistries() {
    }

    static WorkflowRegistryService registry(List<String> roles, List<String> subjectTypes) {
        WorkflowRoleRepository roleRepository = mock(WorkflowRoleRepository.class);
        WorkflowSubjectTypeRepository typeRepository = mock(WorkflowSubjectTypeRepository.class);
        when(roleRepository.findByTenantKeyAndActiveTrueOrderByKeyAsc(anyString())).thenReturn(roles.stream()
            .map(key -> {
                WorkflowRole role = new WorkflowRole();
                role.setKey(key);
                role.setName(key);
                return role;
            }).toList());
        when(typeRepository.findByTenantKeyAndActiveTrueOrderByKeyAsc(anyString())).thenReturn(subjectTypes.stream()
            .map(key -> {
                WorkflowSubjectType type = new WorkflowSubjectType();
                type.setKey(key);
                type.setName(key);
                return type;
            }).toList());
        return new WorkflowRegistryService(roleRepository, typeRepository);
    }
}
