package com.rgapro1.ocaso.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "clients")
public class ClientEntity {
    @NonNull @PrimaryKey public String clientId;
    public String identityType;
    public String identityNumber;
    public String name;
    public String surname;
    public String phone;
    public String email;
    public String address;
    public String city;
    public String postalCode;
    public long createdAt;
    public long updatedAt;
}
