package com.rgapro1.ocaso.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "products")
public class ProductEntity {
    @NonNull @PrimaryKey public String productId;
    public String clientId;
    public String type;
    public String policyNumber;
    public String company;
    public String effectiveDate;
    public String expiryDate;
    public double totalReceipt;
    public boolean active;
}
