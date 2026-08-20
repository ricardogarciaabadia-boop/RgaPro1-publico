package com.rgapro1.ocaso.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(DocumentEntity document);

    @Query("SELECT * FROM documents WHERE clientId = :clientId ORDER BY pageNumber, createdAt")
    List<DocumentEntity> findForClient(String clientId);

    @Query("SELECT * FROM documents WHERE productId = :productId ORDER BY pageNumber")
    List<DocumentEntity> findForProduct(String productId);
}
