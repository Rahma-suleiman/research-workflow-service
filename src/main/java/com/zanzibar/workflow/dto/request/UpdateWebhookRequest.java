
package com.zanzibar.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateWebhookRequest(

    @NotBlank
    @Size(max = 2000)
    String url,

    @NotEmpty
    List<@NotBlank String> eventTypes,

    @NotNull
    Boolean active

) {}
