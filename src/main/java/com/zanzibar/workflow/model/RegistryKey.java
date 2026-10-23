package com.zanzibar.workflow.model;

import java.io.Serializable;
import java.util.Objects;

/** Composite identifier of a per-tenant registry entry (role or subject type). */
public class RegistryKey implements Serializable {

    private String tenantKey;
    private String key;

    protected RegistryKey() {
    }

    public RegistryKey(String tenantKey, String key) {
        this.tenantKey = tenantKey;
        this.key = key;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RegistryKey that)) return false;
        return Objects.equals(tenantKey, that.tenantKey) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantKey, key);
    }
}
