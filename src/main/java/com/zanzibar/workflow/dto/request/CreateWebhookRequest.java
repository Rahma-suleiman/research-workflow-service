
package com.zanzibar.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateWebhookRequest(

    @NotBlank
    @Size(max = 2000)
    String url,

    /** Event wire names such as "instance.transitioned", or "*" for all events. */
    @NotEmpty
    List<@NotBlank String> eventTypes

) {}
