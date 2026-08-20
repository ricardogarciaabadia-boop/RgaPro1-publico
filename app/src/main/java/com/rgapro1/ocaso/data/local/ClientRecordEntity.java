package com.rgapro1.ocaso.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Transitional Room representation of an existing RgaPro client/policy JSON record.
 * rawJson preserves the current schema while the repository is migrated incrementally.
 */
@Entity(tableName = "client_records")
public final class ClientRecordEntity {
    @PrimaryKey
    @NonNull
    public String recordId = "";

    public String identityNumber;
    public String holder;
    public String email;
    public String phone;
    public long updatedAt;
    @NonNull
    public String rawJson = "{}";
}
