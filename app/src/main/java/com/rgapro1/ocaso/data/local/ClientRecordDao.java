package com.rgapro1.ocaso.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ClientRecordDao {
    @Query("SELECT * FROM client_records ORDER BY updatedAt DESC")
    List<ClientRecordEntity> getAll();

    @Query("SELECT * FROM client_records WHERE recordId = :recordId LIMIT 1")
    ClientRecordEntity findById(String recordId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ClientRecordEntity record);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ClientRecordEntity> records);

    @Query("DELETE FROM client_records WHERE recordId = :recordId")
    void deleteById(String recordId);

    @Query("DELETE FROM client_records")
    void deleteAll();
}
