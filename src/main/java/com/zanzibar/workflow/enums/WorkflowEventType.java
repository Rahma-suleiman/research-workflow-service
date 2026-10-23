package com.zanzibar.workflow.enums;

import java.util.Arrays;
import java.util.Optional;

/** Events sent to webhooks; the wire name is what subscribers see. */
public enum WorkflowEventType {
    INSTANCE_STARTED("instance.started"),
    INSTANCE_TRANSITIONED("instance.transitioned"),
    INSTANCE_SLA_BREACHED("instance.sla_breached"),
    INSTANCE_EXTERNAL_APPROVAL_REQUESTED("instance.external_approval_requested");

    private final String wireName;

    WorkflowEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Optional<WorkflowEventType> fromWireName(String value) {
        return Arrays.stream(values())
            .filter(type -> type.wireName.equals(value))
            .findFirst();
    }
}
