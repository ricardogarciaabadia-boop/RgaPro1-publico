package com.rgapro1.ocaso.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ClientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ClientEntity client);

    @Query("SELECT * FROM clients WHERE identityNumber = :identity LIMIT 1")
    ClientEntity findByIdentity(String identity);

    @Query("SELECT * FROM clients WHERE identityNumber = :identity OR name LIKE '%' || :query || '%' OR surname LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%' ORDER BY surname, name")
    List<ClientEntity> search(String query, String identity);

    @Query("SELECT * FROM clients WHERE clientId = :clientId LIMIT 1")
    ClientEntity findById(String clientId);
}
