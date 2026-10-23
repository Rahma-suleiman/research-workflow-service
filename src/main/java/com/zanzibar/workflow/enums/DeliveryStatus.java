
package com.zanzibar.workflow.enums;

public enum DeliveryStatus {
    PENDING,  //waiting for its first or next attempt.
    DELIVERED,  //the endpoint answered with a 2xx status.
    FAILED  //gave up after the maximum number of attempts; can be retried manually.
}
