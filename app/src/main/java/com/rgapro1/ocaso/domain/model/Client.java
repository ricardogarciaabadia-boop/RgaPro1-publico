package com.rgapro1.ocaso.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Modelo de dominio independiente de Android/JSON/SharedPreferences. */
public final class Client {
    private final String id;
    private final String identityType;
    private final String identityNumber;
    private final String name;
    private final String surname;
    private final String phone;
    private final String email;
    private final String address;
    private final List<String> productIds;

    public Client(String id, String identityType, String identityNumber, String name,
                  String surname, String phone, String email, String address,
                  List<String> productIds) {
        this.id = id;
        this.identityType = identityType;
        this.identityNumber = identityNumber;
        this.name = name;
        this.surname = surname;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.productIds = productIds == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(productIds));
    }

    public String getId() { return id; }
    public String getIdentityType() { return identityType; }
    public String getIdentityNumber() { return identityNumber; }
    public String getName() { return name; }
    public String getSurname() { return surname; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public List<String> getProductIds() { return productIds; }
}
