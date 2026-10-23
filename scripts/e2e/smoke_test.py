#!/usr/bin/env python3
"""End-to-end test of a running workflow engine, using a generic purchase-order project.

Start the engine first (scripts/e2e/start-engine.sh), then run:
  python3 scripts/e2e/smoke_test.py

Each run onboards a new tenant, so it can be repeated on the same database.
Environment overrides: ENGINE_URL, ENGINE_ADMIN_USER, ENGINE_ADMIN_PASSWORD.
The engine must allow webhooks to localhost (WORKFLOW_WEBHOOKS_ALLOW_PRIVATE_NETWORK=true)
and should check SLAs often (WORKFLOW_SLA_POLL_INTERVAL_MS=2000); start-engine.sh does both.

Workflow under test (initial status NEW):
  NEW       --SUBMIT          (REQUESTER)                      --> SUBMITTED
  SUBMITTED --APPROVE         (APPROVER, SLA 0 days)           --> APPROVED
  SUBMITTED --REJECT          (APPROVER, comments required)    --> REJECTED
  APPROVED  --CONFIRM_PAYMENT (SYSTEM, EXTERNAL_EVENT, external approval "payment-gateway") --> PAID
"""
import json
import os
import sys
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, HTTPServer

from engine_client import Engine, sign_instance_action, sign_transition, verify_webhook

engine = Engine(os.environ.get("ENGINE_URL", "http://localhost:8081"))
PLATFORM_ADMIN = (os.environ.get("ENGINE_ADMIN_USER", "e2e_admin"),
                  os.environ.get("ENGINE_ADMIN_PASSWORD", "e2e-admin-password-local-only"))

RUN = uuid.uuid4().hex[:8]
TENANT = f"E2E_{RUN.upper()}"
passed, failed = 0, []


def section(title):
    print(f"\n== {title}")


def check(name, condition, detail=None):
    global passed
    if condition:
        passed += 1
        print(f"  PASS  {name}")
    else:
        failed.append(name)
        print(f"  FAIL  {name}" + (f"\n        got: {json.dumps(detail, default=str)[:400]}" if detail is not None else ""))


def status_is(expected, actual):
    return actual == expected


# --- Local webhook receiver -------------------------------------------------
received = []


class Receiver(BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0))).decode("utf-8")
        received.append({"event": self.headers.get("X-Workflow-Event"),
                         "timestamp": self.headers.get("X-Workflow-Timestamp"),
                         "signature": self.headers.get("X-Workflow-Signature"),
                         "body": body, "json": json.loads(body)})
        self.send_response(200)
        self.end_headers()

    def log_message(self, *args):
        pass


receiver = HTTPServer(("127.0.0.1", 0), Receiver)
threading.Thread(target=receiver.serve_forever, daemon=True).start()
WEBHOOK_URL = f"http://127.0.0.1:{receiver.server_port}/workflow-events"


def events_for(instance_id, event_type=None):
    return [r for r in received
            if r["json"]["data"].get("instanceId") == instance_id
            and (event_type is None or r["event"] == event_type)]


def wait_for(predicate, seconds=20):
    deadline = time.time() + seconds
    while time.time() < deadline:
        if predicate():
            return True
        time.sleep(0.5)
    return predicate()


# --- Helpers ----------------------------------------------------------------
def stage(name, frm, to, action, role, order, trigger="HUMAN", sla=None, comments=False,
          external=None):
    return {"name": name, "fromStatus": frm, "toStatus": to, "actionKey": action,
            "assignedRole": role, "triggerType": trigger, "stageOrder": order, "slaDays": sla,
            "requiresComments": comments, "externalApproval": external is not None,
            "externalSystemId": external, "enabled": True}


def act(instance_id, action, role, key, expected=None, trigger="HUMAN", comments=None,
        signed=True, secret=None, credentials=None):
    body = {"expectedStatus": expected, "triggerType": trigger, "actorId": f"user-{role.lower()}",
            "actorRole": role, "comments": comments, "idempotencyKey": f"{RUN}-{key}"}
    headers = {"X-Actor-Signature": sign_instance_action(secret or SECRET, instance_id, action, body)} \
        if signed else {}
    return engine.call("POST", f"/api/instances/{instance_id}/actions/{action}",
                       credentials or SERVICE, body, headers)


def start(subject_id):
    return engine.call("POST", "/api/instances", SERVICE,
                       {"subjectType": "PURCHASE_ORDER", "subjectId": f"{subject_id}-{RUN}"})


# --- 0. Engine is up ----------------------------------------------------------
section("Engine")
s, health = engine.call("GET", "/actuator/health")
check("health endpoint is UP", s == 200 and health and health.get("status") == "UP", health)
if s != 200:
    sys.exit("Engine is not reachable. Start it with scripts/e2e/start-engine.sh")

# --- 1. Platform administration (tenants, client apps) --------------------------
section("Platform: onboard a project as a tenant")
s, body = engine.call("POST", "/api/platform/tenants", PLATFORM_ADMIN,
                      {"key": TENANT, "name": f"E2E purchasing {RUN}"})
check("platform admin creates tenant", s == 201, body)
s, admin_creds = engine.call("POST", f"/api/platform/tenants/{TENANT}/clients", PLATFORM_ADMIN,
                             {"clientId": f"e2e-{RUN}-admin", "kind": "ADMIN"})
check("issue ADMIN client (password shown once)", s == 201 and admin_creds.get("password"), admin_creds)
s, svc_creds = engine.call("POST", f"/api/platform/tenants/{TENANT}/clients", PLATFORM_ADMIN,
                           {"clientId": f"e2e-{RUN}-backend", "kind": "SERVICE"})
check("issue SERVICE client with signing secret", s == 201 and svc_creds.get("signingSecret"), svc_creds)
if failed:
    sys.exit("Cannot continue without tenant credentials.")
ADMIN = (admin_creds["clientId"], admin_creds["password"])
SERVICE = (svc_creds["clientId"], svc_creds["password"])
SECRET = svc_creds["signingSecret"]
s, clients = engine.call("GET", f"/api/platform/tenants/{TENANT}/clients", PLATFORM_ADMIN)
check("client list never shows secrets",
      s == 200 and len(clients) == 2 and all("password" not in c for c in clients), clients)

# --- 2. Registries ------------------------------------------------------------
section("Registries: the project's own roles and subject types")
for role in ("REQUESTER", "APPROVER"):
    s, body = engine.call("POST", "/api/roles", ADMIN, {"key": role, "name": role.title()})
    check(f"register role {role}", s == 201, body)
s, body = engine.call("POST", "/api/roles", ADMIN, {"key": "SYSTEM", "name": "System"})
check("built-in SYSTEM role cannot be registered (400)", s == 400, body)
s, body = engine.call("POST", "/api/roles", ADMIN, {"key": "APPROVER", "name": "Duplicate"})
check("duplicate role rejected (409)", s == 409, body)
s, body = engine.call("POST", "/api/subject-types", ADMIN,
                      {"key": "PURCHASE_ORDER", "name": "Purchase order"})
check("register subject type PURCHASE_ORDER", s == 201, body)

# --- 3. Workflow design ---------------------------------------------------------
section("Workflow design, validation and publishing")
s, body = engine.call("POST", "/api/workflows", ADMIN, {
    "workflowKey": "po-approval", "name": "PO approval (draft)", "initialStatus": "NEW",
    "scope": "SELECTED_TYPES", "subjectTypes": ["UNKNOWN_TYPE"]})
check("workflow for unregistered subject type rejected (400)", s == 400, body)
s, workflow = engine.call("POST", "/api/workflows", ADMIN, {
    "workflowKey": "po-approval", "name": "PO approval (draft)", "initialStatus": "NEW",
    "scope": "SELECTED_TYPES", "subjectTypes": ["PURCHASE_ORDER"]})
check("create draft v1 with initial status NEW", s == 201 and workflow["workflowVersion"] == 1
      and workflow["status"] == "DRAFT", workflow)
WF = workflow["id"]
s, body = engine.call("PUT", f"/api/workflows/{WF}", ADMIN, {
    "name": "PO approval", "initialStatus": "NEW", "scope": "SELECTED_TYPES",
    "subjectTypes": ["PURCHASE_ORDER"]})
check("edit draft settings", s == 200 and body["name"] == "PO approval", body)

s, body = engine.call("POST", f"/api/workflows/{WF}/stages", ADMIN,
                      stage("Bad", "NEW", "SUBMITTED", "SUBMIT", "AUDITOR", 1))
check("stage with unregistered role rejected (400)", s == 400, body)
s, body = engine.call("POST", f"/api/workflows/{WF}/stages", ADMIN,
                      stage("Bad", "NEW", "PAID", "PAY", "APPROVER", 1, trigger="EXTERNAL_EVENT"))
check("EXTERNAL_EVENT stage not assigned to SYSTEM rejected (400)", s == 400, body)

stages = [
    stage("Submit", "NEW", "SUBMITTED", "SUBMIT", "REQUESTER", 1),
    stage("Approve", "SUBMITTED", "APPROVED", "APPROVE", "APPROVER", 2, sla=0),
    stage("Reject", "SUBMITTED", "REJECTED", "REJECT", "APPROVER", 3, comments=True),
    stage("Confirm payment", "APPROVED", "PAID", "CONFIRM_PAYMENT", "SYSTEM", 4,
          trigger="EXTERNAL_EVENT", external="payment-gateway"),
]
for definition in stages:
    s, body = engine.call("POST", f"/api/workflows/{WF}/stages", ADMIN, definition)
    check(f"add stage {definition['actionKey']}", s == 201, body)

s, body = engine.call("POST", f"/api/workflows/{WF}/stages", ADMIN,
                      stage("Orphan", "ON_HOLD", "SUBMITTED", "RESUME", "REQUESTER", 9))
orphan_id = next((st["id"] for st in (body or {}).get("stages", []) if st["actionKey"] == "RESUME"), None)
s, report = engine.call("GET", f"/api/workflows/{WF}/validation", ADMIN)
check("validation catches transition unreachable from NEW",
      s == 200 and not report["publishable"]
      and any("unreachable" in e for e in report["errors"]), report)
s, body = engine.call("POST", f"/api/workflows/{WF}/publish", ADMIN)
check("publishing an invalid workflow is refused (422)", s == 422, body)
s, _ = engine.call("DELETE", f"/api/workflows/{WF}/stages/{orphan_id}", ADMIN)
check("delete the orphan stage", s == 204)
s, report = engine.call("GET", f"/api/workflows/{WF}/validation", ADMIN)
check("validation now publishable; terminal statuses reported as warnings",
      s == 200 and report["publishable"] and report["warnings"], report)
s, body = engine.call("POST", f"/api/workflows/{WF}/publish", ADMIN)
check("publish v1", s == 200 and body["status"] == "PUBLISHED" and body["active"], body)
s, body = engine.call("POST", f"/api/workflows/{WF}/stages", ADMIN,
                      stage("Late", "NEW", "CANCELLED", "CANCEL", "REQUESTER", 5))
check("published workflow cannot be edited (409)", s == 409, body)
s, coverage = engine.call("GET", "/api/workflows/coverage", ADMIN)
check("coverage: PURCHASE_ORDER resolves to exactly one workflow",
      s == 200 and coverage["allSubjectTypesCovered"], coverage)

# --- 4. Webhook subscription ------------------------------------------------------
section("Webhook subscription")
s, hook = engine.call("POST", "/api/webhooks", ADMIN, {"url": WEBHOOK_URL, "eventTypes": ["*"]})
check("register webhook (signing secret shown once)", s == 201 and hook.get("signingSecret"), hook)
HOOK_SECRET = hook["signingSecret"] if s == 201 else ""
HOOK_ID = hook["webhook"]["id"] if s == 201 else None
s, body = engine.call("POST", "/api/webhooks", ADMIN,
                      {"url": WEBHOOK_URL, "eventTypes": ["order.created"]})
check("unknown event type rejected (400)", s == 400, body)

# --- 5. Instance lifecycle ----------------------------------------------------------
section("Instance mode: the engine owns the status")
s, po1 = start("PO-1")
check("start instance at initial status NEW", s == 201 and po1["currentStatus"] == "NEW", po1)
PO1 = po1["id"]
check("NEW has no SLA (SUBMIT has none)", po1["dueAt"] is None, po1)
s, again = start("PO-1")
check("starting the same subject again returns it (200)", s == 200 and again["id"] == PO1, again)
s, found = engine.call("GET", f"/api/instances?subjectType=PURCHASE_ORDER&subjectId=PO-1-{RUN}", SERVICE)
check("find instance by subject", s == 200 and found["id"] == PO1, found)
s, view = engine.call("GET", f"/api/instances/{PO1}?actorRole=REQUESTER", SERVICE)
check("available actions filtered by role (REQUESTER -> SUBMIT)",
      s == 200 and [a["actionKey"] for a in view["availableActions"]] == ["SUBMIT"], view)
s, view = engine.call("GET", f"/api/instances/{PO1}?actorRole=APPROVER", SERVICE)
check("APPROVER has no action in NEW", s == 200 and view["availableActions"] == [], view)

s, body = act(PO1, "SUBMIT", "REQUESTER", "po1-unsigned", signed=False)
check("unsigned action rejected (401)", s == 401, body)
s, body = act(PO1, "SUBMIT", "REQUESTER", "po1-badsig", secret="x" * 40)
check("action signed with the wrong secret rejected (401)", s == 401, body)
s, body = act(PO1, "SUBMIT", "APPROVER", "po1-wrongrole")
check("role not assigned to the action rejected (403)", s == 403, body)
s, body = act(PO1, "APPROVE", "APPROVER", "po1-wrongstatus")
check("action not available from current status rejected (422)", s == 422, body)
s, body = act(PO1, "SUBMIT", "REQUESTER", "po1-stale", expected="SUBMITTED")
check("stale expectedStatus rejected (409)", s == 409, body)

s, body = act(PO1, "SUBMIT", "REQUESTER", "po1-submit", expected="NEW")
check("SUBMIT moves NEW -> SUBMITTED", s == 200 and body["instance"]["currentStatus"] == "SUBMITTED", body)
check("SUBMITTED gets an SLA due date (APPROVE has 0 days)", s == 200 and body["instance"]["dueAt"], body)
s, body = act(PO1, "SUBMIT", "REQUESTER", "po1-submit", expected="NEW")
check("retry with the same idempotency key is replayed, not re-applied",
      s == 200 and body["replayed"] is True, body)
s, body = act(PO1, "APPROVE", "APPROVER", "po1-submit")
check("same idempotency key for a different action rejected (409)", s == 409, body)
s, overdue = engine.call("GET", "/api/instances/overdue", SERVICE)
check("overdue list contains PO-1", s == 200 and any(o["instanceId"] == PO1 for o in overdue), overdue)

s, body = act(PO1, "REJECT", "APPROVER", "po1-reject-nocomment")
check("REJECT without required comments rejected (422)", s == 422, body)
s, body = act(PO1, "APPROVE", "APPROVER", "po1-approve")
check("APPROVE moves SUBMITTED -> APPROVED", s == 200 and body["toStatus"] == "APPROVED", body)
s, body = act(PO1, "CONFIRM_PAYMENT", "SYSTEM", "po1-pay-human")
check("external action with HUMAN trigger rejected (422)", s == 422, body)
s, body = act(PO1, "CONFIRM_PAYMENT", "APPROVER", "po1-pay-role", trigger="EXTERNAL_EVENT")
check("external action by a human role rejected (403)", s == 403, body)
s, body = act(PO1, "CONFIRM_PAYMENT", "SYSTEM", "po1-pay", trigger="EXTERNAL_EVENT")
check("payment callback moves APPROVED -> PAID (terminal)",
      s == 200 and body["instance"]["currentStatus"] == "PAID" and body["instance"]["terminal"], body)
s, history = engine.call("GET", f"/api/instances/{PO1}/history", SERVICE)
check("history has the 3 applied transitions in order",
      s == 200 and [h["actionKey"] for h in history] == ["SUBMIT", "APPROVE", "CONFIRM_PAYMENT"], history)

# --- 6. Concurrency -------------------------------------------------------------------
section("Concurrency: two approvers act at the same moment")
s, po2 = start("PO-2")
PO2 = po2["id"]
act(PO2, "SUBMIT", "REQUESTER", "po2-submit")
with ThreadPoolExecutor(max_workers=2) as pool:
    approve = pool.submit(act, PO2, "APPROVE", "APPROVER", "po2-approve")
    reject = pool.submit(act, PO2, "REJECT", "APPROVER", "po2-reject", comments="Over budget")
    outcomes = [approve.result()[0], reject.result()[0]]
check("exactly one of APPROVE / REJECT succeeds", sorted(outcomes)[0] == 200 and outcomes.count(200) == 1,
      outcomes)
s, history = engine.call("GET", f"/api/instances/{PO2}/history", SERVICE)
check("only the winning action is in the history", s == 200 and len(history) == 2, history)

# --- 7. Versioning ----------------------------------------------------------------------
section("Versioning: in-flight instances keep their version")
s, po3 = start("PO-3")
PO3 = po3["id"]
s, draft = engine.call("POST", f"/api/workflows/{WF}/versions", ADMIN)
check("create draft v2 from v1 (stages copied)",
      s == 201 and draft["workflowVersion"] == 2 and len(draft["stages"]) == 4, draft)
s, body = engine.call("POST", f"/api/workflows/{draft['id']}/publish", ADMIN)
check("publish v2", s == 200 and body["status"] == "PUBLISHED", body)
s, old = engine.call("GET", f"/api/workflows/{WF}", ADMIN)
check("v1 is archived automatically", s == 200 and old["status"] == "ARCHIVED" and not old["active"], old)
s, body = act(PO3, "SUBMIT", "REQUESTER", "po3-submit")
check("instance started on v1 still works on v1",
      s == 200 and body["instance"]["workflowVersion"] == 1, body)
s, po4 = start("PO-4")
check("new instances use v2", s == 201 and po4["workflowVersion"] == 2, po4)

# --- 8. Validate-only mode ----------------------------------------------------------------
section("Validate-only mode (caller stores the status)")
s, resolved = engine.call("GET", "/api/workflows/resolve?subjectType=PURCHASE_ORDER", SERVICE)
check("resolve active workflow for a subject type", s == 200 and resolved["workflowVersion"] == 2, resolved)
transition = {"workflowDefinitionId": resolved["id"], "workflowVersion": 2,
              "subjectId": f"EXT-{RUN}", "currentStatus": "NEW", "actionKey": "SUBMIT",
              "triggerType": "HUMAN", "actorId": "user-requester", "actorRole": "REQUESTER",
              "comments": "", "idempotencyKey": f"{RUN}-validate-1"}
s, body = engine.call("POST", "/api/transitions/execute", SERVICE, transition,
                      {"X-Actor-Signature": sign_transition(SECRET, transition)})
check("signed transition validated with toStatus SUBMITTED",
      s == 200 and body["decision"] == "VALIDATED" and body["toStatus"] == "SUBMITTED", body)
s, body = engine.call("GET", f"/api/workflows/{resolved['id']}/versions/1", SERVICE)
check("wrong pinned version reported as conflict (409)", s == 409, body)

# --- 9. Tenant isolation ------------------------------------------------------------------
section("Tenant isolation")
s, body = engine.call("GET", f"/api/workflows/{WF}", PLATFORM_ADMIN)
check("DEFAULT tenant cannot see this tenant's workflow (404)", s == 404, body)
s, body = engine.call("GET", f"/api/instances/{PO1}", PLATFORM_ADMIN)
check("DEFAULT tenant cannot see this tenant's instance (404)", s == 404, body)
s, body = engine.call("GET", "/api/platform/tenants", ADMIN)
check("tenant admin cannot use the platform API (403)", s == 403, body)
s, body = engine.call("POST", "/api/instances", ADMIN,
                      {"subjectType": "PURCHASE_ORDER", "subjectId": f"X-{RUN}"})
check("tenant admin cannot start instances (403)", s == 403, body)
s, body = engine.call("GET", "/api/workflows", SERVICE)
check("service client cannot administer workflows (403)", s == 403, body)

# --- 10. Webhooks and SLA ------------------------------------------------------------------
section("Webhooks and SLA events (delivered by background jobs)")
s, po5 = start("PO-5")
PO5 = po5["id"]
act(PO5, "SUBMIT", "REQUESTER", "po5-submit")  # SLA 0 days: overdue immediately
check("instance.started, instance.transitioned delivered for PO-1",
      wait_for(lambda: events_for(PO1, "instance.started") and len(events_for(PO1, "instance.transitioned")) == 3),
      [r["event"] for r in events_for(PO1)])
check("instance.external_approval_requested names the payment gateway",
      wait_for(lambda: any(r["json"]["data"].get("externalSystemId") == "payment-gateway"
                           for r in events_for(PO1, "instance.external_approval_requested"))),
      [r["event"] for r in events_for(PO1)])
check("instance.sla_breached delivered once for overdue PO-5",
      wait_for(lambda: len(events_for(PO5, "instance.sla_breached")) == 1)
      and (time.sleep(5) or len(events_for(PO5, "instance.sla_breached")) == 1),
      [r["event"] for r in events_for(PO5)])
mine = [r for r in received if r["json"].get("tenantKey") == TENANT]
check("every webhook carries a valid signature",
      mine and all(verify_webhook(HOOK_SECRET, r["timestamp"], r["body"], r["signature"]) for r in mine),
      len(mine))
check("no events from rejected actions (only applied transitions are announced)",
      len(events_for(PO1, "instance.transitioned")) == 3, [r["event"] for r in events_for(PO1)])
s, deliveries = engine.call("GET", f"/api/webhooks/{HOOK_ID}/deliveries", ADMIN)
check("delivery log shows deliveries as DELIVERED",
      s == 200 and deliveries and all(d["status"] == "DELIVERED" for d in deliveries),
      [d["status"] for d in deliveries or []])

# --- 11. Credential lifecycle -----------------------------------------------------------------
section("Credential rotation and deactivation")
s, rotated = engine.call("POST", f"/api/platform/clients/{SERVICE[0]}/rotate-credentials", PLATFORM_ADMIN)
check("rotate SERVICE client credentials", s == 200 and rotated.get("signingSecret"), rotated)
s, _ = engine.call("GET", f"/api/instances/{PO1}", SERVICE)
check("old password stops working (401)", s == 401, s)
NEW_SERVICE = (SERVICE[0], rotated["password"])
s, po6 = engine.call("POST", "/api/instances", NEW_SERVICE,
                     {"subjectType": "PURCHASE_ORDER", "subjectId": f"PO-6-{RUN}"})
s, body = act(po6["id"], "SUBMIT", "REQUESTER", "po6-oldsecret", credentials=NEW_SERVICE)
check("old signing secret stops working (401)", s == 401, body)
s, body = act(po6["id"], "SUBMIT", "REQUESTER", "po6-submit", credentials=NEW_SERVICE,
              secret=rotated["signingSecret"])
check("new password and signing secret work", s == 200, body)
s, _ = engine.call("PUT", f"/api/platform/clients/{ADMIN[0]}", PLATFORM_ADMIN, {"active": False})
s, _ = engine.call("GET", "/api/workflows", ADMIN)
check("deactivated client is locked out (401)", s == 401, s)

# --- Summary ------------------------------------------------------------------------------------
receiver.shutdown()
print(f"\nTenant used: {TENANT}")
print(f"{passed} passed, {len(failed)} failed")
if failed:
    print("Failed checks:\n  - " + "\n  - ".join(failed))
    sys.exit(1)
