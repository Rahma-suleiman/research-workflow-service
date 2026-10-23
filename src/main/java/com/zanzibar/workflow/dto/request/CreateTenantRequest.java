
package com.zanzibar.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(

    @NotBlank
    @Size(max = 60)
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]*")
    String key,

    @NotBlank
    @Size(max = 200)
    String name

) {}
