"""Minimal client for the workflow engine API (Python standard library only).

Used by smoke_test.py and sign.py. The signing functions mirror
WorkflowActorSignatureService and WebhookDispatcher exactly.
"""
import base64
import hashlib
import hmac
import json
import urllib.error
import urllib.request


class Engine:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")

    def call(self, method, path, credentials=None, body=None, headers=None):
        """Returns (status, parsed JSON body or None). Never raises on HTTP errors."""
        data = None if body is None else json.dumps(body).encode("utf-8")
        request = urllib.request.Request(self.base_url + path, method=method, data=data)
        request.add_header("Content-Type", "application/json")
        if credentials:
            token = base64.b64encode(f"{credentials[0]}:{credentials[1]}".encode()).decode()
            request.add_header("Authorization", "Basic " + token)
        for name, value in (headers or {}).items():
            request.add_header(name, value)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                raw = response.read()
                return response.status, json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            raw = error.read()
            try:
                return error.code, json.loads(raw) if raw else None
            except ValueError:
                return error.code, raw.decode("utf-8", "replace")


def _normalize(value):
    value = (value or "").strip().upper()
    return value[5:] if value.startswith("ROLE_") else value


def _length_prefixed(prefix, values):
    return prefix + "".join(f"{len(v.encode('utf-8'))}:{v}" for v in values)


def _hmac_b64url(secret, payload):
    digest = hmac.new(secret.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256).digest()
    return base64.urlsafe_b64encode(digest).rstrip(b"=").decode()


def sign_instance_action(secret, instance_id, action_key, body):
    """X-Actor-Signature for POST /api/instances/{id}/actions/{actionKey}."""
    return _hmac_b64url(secret, _length_prefixed("workflow-instance-action-v1|", [
        str(instance_id),
        _normalize(action_key),
        _normalize(body.get("expectedStatus")),
        body["triggerType"],
        body["actorId"],
        _normalize(body["actorRole"]),
        body.get("comments") or "",
        body["idempotencyKey"],
    ]))


def sign_transition(secret, body):
    """X-Actor-Signature for POST /api/transitions/execute (validate-only mode)."""
    return _hmac_b64url(secret, _length_prefixed("workflow-actor-v1|", [
        str(body["workflowDefinitionId"]),
        str(body["workflowVersion"]),
        body["subjectId"],
        _normalize(body["currentStatus"]),
        _normalize(body["actionKey"]),
        body["triggerType"],
        body["actorId"],
        _normalize(body["actorRole"]),
        body.get("comments") or "",
        body["idempotencyKey"],
    ]))


def verify_webhook(secret, timestamp, raw_body, signature_header):
    """Checks X-Workflow-Signature (v1=hex HMAC-SHA256 of 'timestamp.body')."""
    expected = "v1=" + hmac.new(secret.encode("utf-8"),
                                f"{timestamp}.{raw_body}".encode("utf-8"),
                                hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature_header or "")
