
package com.zanzibar.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Renames or (de)activates a registered role or subject type. The key never changes. */
public record UpdateRegistryEntryRequest(

    @NotBlank
    @Size(max = 200)
    String name,

    String description,

    @NotNull
    Boolean active

) {}
