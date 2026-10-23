
package com.zanzibar.workflow.dto.response;

import com.zanzibar.workflow.enums.ClientAppKind;

import java.time.Instant;

/** Client app details without any credential material. */
public record ClientAppResponse(
    String clientId,
    String tenantKey,
    ClientAppKind kind,
    boolean active,
    Instant createdAt,
    Instant credentialsRotatedAt
) {}
