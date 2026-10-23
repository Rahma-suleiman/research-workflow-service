package com.zanzibar.workflow.service;

import com.zanzibar.workflow.exception.WorkflowException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebhookUrlPolicyTest {

    private final WebhookUrlPolicy strict = new WebhookUrlPolicy(true, false);

    @ParameterizedTest
    @ValueSource(strings = {
        "https://127.0.0.1/hook",
        "https://10.1.2.3/hook",
        "https://192.168.0.10/hook",
        "https://172.16.5.4/hook",
        "https://169.254.169.254/latest/meta-data",
        "https://100.64.0.1/hook",
        "https://[::1]/hook",
        "https://[fd00::1]/hook",
        "https://0.0.0.0/hook"
    })
    void rejectsPrivateLoopbackAndMetadataAddresses(String url) {
        assertThrows(WorkflowException.class, () -> strict.check(url));
    }

    @Test
    void acceptsPublicHttpsAddress() {
        assertEquals("https://93.184.216.34/hook", strict.check("https://93.184.216.34/hook").toString());
    }

    @Test
    void requiresHttpsWhenConfigured() {
        assertThrows(WorkflowException.class, () -> strict.check("http://93.184.216.34/hook"));
        assertDoesNotThrow(() -> new WebhookUrlPolicy(false, false).check("http://93.184.216.34/hook"));
    }

    @Test
    void rejectsCredentialsAndOtherSchemes() {
        assertThrows(WorkflowException.class, () -> strict.check("https://user:pw@93.184.216.34/hook"));
        assertThrows(WorkflowException.class, () -> strict.check("ftp://93.184.216.34/hook"));
        assertThrows(WorkflowException.class, () -> strict.check("not a url"));
    }

    @Test
    void privateAddressesCanBeAllowedForInternalDeployments() {
        assertDoesNotThrow(() -> new WebhookUrlPolicy(false, true).check("http://127.0.0.1:8080/hook"));
    }
}
