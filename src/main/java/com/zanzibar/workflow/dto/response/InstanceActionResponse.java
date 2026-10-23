
package com.zanzibar.workflow.dto.response;

import java.util.UUID;

/**
 * Result of an applied action. replayed is true when the same idempotency key
 * was already applied and the original result is returned without changes.
 */
public record InstanceActionResponse(
    UUID transitionLogId,
    String actionKey,
    String fromStatus,
    String toStatus,
    boolean replayed,
    InstanceResponse instance
) {}
