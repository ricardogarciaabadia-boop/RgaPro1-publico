package com.rgapro1.ocaso.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {ClientRecordEntity.class}, version = 1, exportSchema = false)
public abstract class RgaDatabase extends RoomDatabase {
    public abstract ClientRecordDao clientRecordDao();

    private static volatile RgaDatabase INSTANCE;

    public static RgaDatabase getInstance(Context context) {
        RgaDatabase instance = INSTANCE;
        if (instance != null) return instance;
        synchronized (RgaDatabase.class) {
            instance = INSTANCE;
            if (instance == null) {
                instance = Room.databaseBuilder(
                        context.getApplicationContext(),
                        RgaDatabase.class,
                        "rgapro.db"
                ).build();
                INSTANCE = instance;
            }
        }
        return instance;
    }
}
