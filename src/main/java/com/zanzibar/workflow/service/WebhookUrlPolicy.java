package com.zanzibar.workflow.service;

import com.zanzibar.workflow.exception.WorkflowException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Guards against tenants pointing webhooks at the engine's own network (SSRF).
 * Checked when a webhook is saved and again before every delivery, because DNS
 * answers can change. Redirects are never followed by the dispatcher.
 */
@Component
public class WebhookUrlPolicy {

    private final boolean requireHttps;
    private final boolean allowPrivateNetwork;

    public WebhookUrlPolicy(
        @Value("${workflow.webhooks.require-https:false}") boolean requireHttps,
        @Value("${workflow.webhooks.allow-private-network:false}") boolean allowPrivateNetwork
    ) {
        this.requireHttps = requireHttps;
        this.allowPrivateNetwork = allowPrivateNetwork;
    }

    /** Returns the normalized URI or throws a 400 explaining why it is not allowed. */
    public URI check(String url) {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.trim());
        } catch (IllegalArgumentException exception) {
            throw new WorkflowException("Invalid webhook URL.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && !requireHttps)) {
            throw new WorkflowException(requireHttps
                ? "Webhook URLs must use https."
                : "Webhook URLs must use http or https.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new WorkflowException("Webhook URL must include a host.");
        }
        if (uri.getUserInfo() != null) {
            throw new WorkflowException("Webhook URLs must not contain credentials.");
        }
        if (!allowPrivateNetwork) {
            rejectPrivateAddresses(uri.getHost());
        }
        return uri;
    }

    private void rejectPrivateAddresses(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new WorkflowException("Webhook host cannot be resolved: " + host);
        }
        for (InetAddress address : addresses) {
            if (isPrivate(address)) {
                throw new WorkflowException(
                    "Webhook URLs must not point to private, loopback or link-local addresses.");
            }
        }
    }

    private boolean isPrivate(InetAddress address) {
        byte[] bytes = address.getAddress();
        boolean uniqueLocalIpv6 = bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
        boolean carrierGradeNat = bytes.length == 4
            && (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64;
        return address.isLoopbackAddress() || address.isSiteLocalAddress()
            || address.isLinkLocalAddress() || address.isAnyLocalAddress()
            || address.isMulticastAddress() || uniqueLocalIpv6 || carrierGradeNat;
    }
}
