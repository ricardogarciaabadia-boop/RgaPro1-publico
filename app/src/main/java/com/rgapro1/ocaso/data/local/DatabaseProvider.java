package com.rgapro1.ocaso.data.local;

import android.content.Context;
import androidx.room.Room;

public final class DatabaseProvider {
    private static volatile AppDatabase instance;

    private DatabaseProvider() {}

    public static AppDatabase get(Context context) {
        AppDatabase result = instance;
        if (result == null) {
            synchronized (DatabaseProvider.class) {
                result = instance;
                if (result == null) {
                    result = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "rgapro.db")
                            // Never silently destroy client/document data on a schema mismatch.
                            // Future schema changes must ship an explicit Room Migration.
                            .build();
                    instance = result;
                }
            }
        }
        return result;
    }
}
