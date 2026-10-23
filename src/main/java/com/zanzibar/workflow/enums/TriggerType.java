
package com.zanzibar.workflow.enums;

public enum TriggerType {
    HUMAN,  //an authenticated end user (applicant, staff member, ...) performs an action.
    SYSTEM,  //a trusted backend operation triggers a transition.
    EXTERNAL_EVENT  //a verified external event (payment, signature, third-party callback) triggers a transition.
}
