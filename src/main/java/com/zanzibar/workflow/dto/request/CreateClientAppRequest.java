
package com.zanzibar.workflow.dto.request;

import com.zanzibar.workflow.enums.ClientAppKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateClientAppRequest(

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[a-z][a-z0-9_.-]*")
    String clientId,

    @NotNull
    ClientAppKind kind

) {}
