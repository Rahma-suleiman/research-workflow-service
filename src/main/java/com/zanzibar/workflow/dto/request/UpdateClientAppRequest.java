
package com.zanzibar.workflow.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateClientAppRequest(

    @NotNull
    Boolean active

) {}
