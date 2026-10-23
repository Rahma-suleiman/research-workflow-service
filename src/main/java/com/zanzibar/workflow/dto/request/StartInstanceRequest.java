
package com.zanzibar.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StartInstanceRequest(

    @NotBlank
    @Size(max = 60)
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]*")
    String subjectType,

    @NotBlank
    @Size(max = 150)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]*")
    String subjectId

) {}
