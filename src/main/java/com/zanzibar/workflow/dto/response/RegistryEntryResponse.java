
package com.zanzibar.workflow.dto.response;

import java.time.Instant;

public record RegistryEntryResponse(
    String key,
    String name,
    String description,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {}
