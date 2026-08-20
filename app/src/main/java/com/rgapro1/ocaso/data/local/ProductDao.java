package com.rgapro1.ocaso.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ProductEntity product);

    @Query("SELECT * FROM products WHERE clientId = :clientId ORDER BY expiryDate")
    List<ProductEntity> findForClient(String clientId);

    @Query("SELECT * FROM products WHERE expiryDate IS NOT NULL AND expiryDate != ''")
    List<ProductEntity> allWithExpiry();
}
