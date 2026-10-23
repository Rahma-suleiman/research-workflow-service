
package com.zanzibar.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(

    @NotBlank
    @Size(max = 200)
    String name,

    @NotNull
    Boolean active

) {}
