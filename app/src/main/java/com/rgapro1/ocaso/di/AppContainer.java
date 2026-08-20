package com.rgapro1.ocaso.di;

import android.content.Context;
import com.rgapro1.ocaso.domain.repository.ClientRepository;
import com.rgapro1.ocaso.domain.repository.DocumentRepository;
import com.rgapro1.ocaso.domain.repository.ProductRepository;

/**
 * Punto único de composición de dependencias para el proyecto Java actual.
 * En esta fase no introduce un framework DI: permite migrar sin romper la app.
 */
public final class AppContainer {
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final DocumentRepository documentRepository;

    public AppContainer(Context context,
                        ClientRepository clientRepository,
                        ProductRepository productRepository,
                        DocumentRepository documentRepository) {
        if (context == null) throw new IllegalArgumentException("context == null");
        this.clientRepository = clientRepository;
        this.productRepository = productRepository;
        this.documentRepository = documentRepository;
    }

    public ClientRepository clients() { return clientRepository; }
    public ProductRepository products() { return productRepository; }
    public DocumentRepository documents() { return documentRepository; }
}
