
package com.zanzibar.workflow.dto.response;

/** A webhook with its newly generated signing secret, returned only once. */
public record IssuedWebhookSecretResponse(
    WebhookResponse webhook,
    String signingSecret,
    String message
) {}
