package com.rgapro1.ocaso.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "documents")
public class DocumentEntity {
    @NonNull @PrimaryKey public String documentId;
    public String clientId;
    public String productId;
    public String documentType;
    public String side;
    public int pageNumber;
    public String localPath;
    public String ocrText;
    public long createdAt;
}
