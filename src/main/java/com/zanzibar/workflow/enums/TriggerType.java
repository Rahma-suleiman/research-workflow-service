
package com.zanzibar.workflow.enums;

public enum TriggerType {
    HUMAN,  //an authenticated applicant or staff member performs an action.
    SYSTEM,  //a trusted backend operation triggers a transition.
    PAYMENT  //a verified payment event triggers a transition.
}
