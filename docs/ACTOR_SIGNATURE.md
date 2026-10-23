# Trusted actor signature for transition requests

The transition endpoint requires both the `WORKFLOW_SERVICE` HTTP Basic account
and an `X-Actor-Signature` header. HTTP Basic authenticates the calling service;
the HMAC signature protects the actor ID, role, trigger, and transition request
from being changed by a caller that does not possess the shared signing secret.

## Configuration

Set `WORKFLOW_ACTOR_SIGNING_SECRET` to a cryptographically random secret of at
least 32 characters in both the trusted calling backend and this service.
Do not commit the secret or send it to the browser. Use a secret manager in
production. The `prod`/`production` profile refuses startup when known sample or
short secrets are configured.

## Header

Send the request body as usual and add:

`X-Actor-Signature: <base64url-without-padding HMAC-SHA256 signature>`

The HMAC is over the canonical payload version `workflow-actor-v1|`. Values
are appended in this order: workflowDefinitionId, workflowVersion, subjectId,
upper-case trimmed currentStatus, upper-case trimmed actionKey, triggerType,
actorId, upper-case trimmed actorRole with a leading `ROLE_` removed, comments,
and idempotencyKey. Each value is encoded as `<UTF-8 byte length>:<value>`;
there are no separators between the length-prefixed values. Null comments are
encoded as an empty string. UUIDs and enum values use their normal string form.
The HMAC key is the UTF-8 bytes of the shared secret. Encode the resulting HMAC
bytes using Base64 URL encoding without padding.

The backend must sign the exact values it sends. Any change to the role, actor,
subject, action, status, trigger, comments, workflow version, or idempotency
key invalidates the signature. This is an internal service contract; never expose
a signing endpoint to browser clients.

For production, also restrict network access to this endpoint and rotate the
shared secret under a coordinated deployment plan. A future asymmetric JWT
assertion may be preferable if the two services need independent key ownership.
