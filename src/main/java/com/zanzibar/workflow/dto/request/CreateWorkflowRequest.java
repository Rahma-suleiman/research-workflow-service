
package com.zanzibar.workflow.dto.request;

import com.zanzibar.workflow.enums.WorkflowScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateWorkflowRequest(

    @NotBlank
    @Size(max = 100)
    String workflowKey,

    @NotBlank
    @Size(max = 200)
    String name,

    String description,

    @NotNull
    WorkflowScope scope,

    Set<@NotBlank @Size(max = 60) String> applicableApplicationTypes

) {}
