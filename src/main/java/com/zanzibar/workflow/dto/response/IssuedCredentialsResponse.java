
package com.zanzibar.workflow.dto.response;

import com.zanzibar.workflow.enums.ClientAppKind;

/**
 * Newly generated credentials. They are returned only once and cannot be
 * retrieved again; rotate them if they are lost.
 */
public record IssuedCredentialsResponse(
    String clientId,
    String tenantKey,
    ClientAppKind kind,
    String password,
    String signingSecret,
    String message
) {}
