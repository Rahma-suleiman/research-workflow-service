
package com.zanzibar.workflow.enums;

public enum ClientAppKind {
    ADMIN,  //manages one tenant's registries and workflow definitions.
    SERVICE  //a tenant's backend: resolves workflows and executes transitions.
}
