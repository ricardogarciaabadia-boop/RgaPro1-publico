package com.rgapro1.ocaso.domain.repository;

import com.rgapro1.ocaso.domain.model.Document;
import java.util.List;

public interface DocumentRepository {
    List<Document> findByClient(String clientId);
    Document save(Document document);
}
