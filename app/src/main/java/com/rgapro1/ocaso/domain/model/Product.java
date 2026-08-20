package com.rgapro1.ocaso.domain.model;

import java.util.Collections;

/** Producto asegurador; una póliza concreta puede cambiar durante su vida. */
public final class Product {
    private final String id;
    private final String clientId;
    private final String type;
    private final String policyNumber;
    private final String insurer;
    private final String effectiveDate;
    private final String expiryDate;
    private final boolean active;

    public Product(String id, String clientId, String type, String policyNumber,
                   String insurer, String effectiveDate, String expiryDate, boolean active) {
        this.id = id;
        this.clientId = clientId;
        this.type = type;
        this.policyNumber = policyNumber;
        this.insurer = insurer;
        this.effectiveDate = effectiveDate;
        this.expiryDate = expiryDate;
        this.active = active;
    }

    public String getId() { return id; }
    public String getClientId() { return clientId; }
    public String getType() { return type; }
    public String getPolicyNumber() { return policyNumber; }
    public String getInsurer() { return insurer; }
    public String getEffectiveDate() { return effectiveDate; }
    public String getExpiryDate() { return expiryDate; }
    public boolean isActive() { return active; }
}
