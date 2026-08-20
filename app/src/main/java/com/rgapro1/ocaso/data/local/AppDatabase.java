package com.rgapro1.ocaso.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {ClientEntity.class, ProductEntity.class, DocumentEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract ClientDao clientDao();
    public abstract ProductDao productDao();
    public abstract DocumentDao documentDao();
}
