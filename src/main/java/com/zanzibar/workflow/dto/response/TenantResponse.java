
package com.zanzibar.workflow.dto.response;

import java.time.Instant;

public record TenantResponse(
    String key,
    String name,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {}
