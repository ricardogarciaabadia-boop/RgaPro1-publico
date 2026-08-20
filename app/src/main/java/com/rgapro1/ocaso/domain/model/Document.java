package com.rgapro1.ocaso.domain.model;

/** Documento asociado a un cliente/producto sin depender de una ruta de UI. */
public final class Document {
    private final String id;
    private final String clientId;
    private final String productId;
    private final String type;
    private final String localPath;
    private final int pageNumber;

    public Document(String id, String clientId, String productId, String type,
                    String localPath, int pageNumber) {
        this.id = id;
        this.clientId = clientId;
        this.productId = productId;
        this.type = type;
        this.localPath = localPath;
        this.pageNumber = pageNumber;
    }

    public String getId() { return id; }
    public String getClientId() { return clientId; }
    public String getProductId() { return productId; }
    public String getType() { return type; }
    public String getLocalPath() { return localPath; }
    public int getPageNumber() { return pageNumber; }
}
