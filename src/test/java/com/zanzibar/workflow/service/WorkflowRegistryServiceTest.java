package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.request.CreateRegistryEntryRequest;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.model.WorkflowRole;
import com.zanzibar.workflow.repository.WorkflowRoleRepository;
import com.zanzibar.workflow.repository.WorkflowSubjectTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowRegistryServiceTest {

    private final WorkflowRoleRepository roles = mock(WorkflowRoleRepository.class);
    private final WorkflowSubjectTypeRepository types = mock(WorkflowSubjectTypeRepository.class);
    private final WorkflowRegistryService registry = new WorkflowRegistryService(roles, types);

    @Test
    void activeRolesAlwaysIncludeBuiltInSystemRole() {
        when(roles.findByActiveTrueOrderByKeyAsc()).thenReturn(List.of());
        assertTrue(registry.activeRoleKeys().contains("SYSTEM"));
    }

    @Test
    void systemRoleCannotBeRegistered() {
        assertThrows(WorkflowException.class, () -> registry.createRole(
            new CreateRegistryEntryRequest("system", "System", null)));
        verify(roles, never()).save(any());
    }

    @Test
    void roleKeyIsNormalizedToUpperCase() {
        when(roles.save(any(WorkflowRole.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals("CASE_WORKER", registry.createRole(
            new CreateRegistryEntryRequest(" case_worker ", "Case worker", null)).key());
    }

    @Test
    void duplicateRoleIsRejectedWithConflict() {
        when(roles.existsById("REVIEWER")).thenReturn(true);
        WorkflowException error = assertThrows(WorkflowException.class, () -> registry.createRole(
            new CreateRegistryEntryRequest("REVIEWER", "Reviewer", null)));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }
}
