package com.rgapro1.ocaso.domain.repository;

import com.rgapro1.ocaso.domain.model.Client;
import java.util.List;
import java.util.Optional;

/** Contrato de datos: la UI no debe conocer SharedPreferences, Room o JSON. */
public interface ClientRepository {
    List<Client> search(String query);
    Optional<Client> findByIdentity(String identityType, String identityNumber);
    Client save(Client client);
}
