package com.rgapro1.ocaso.domain.repository;

import com.rgapro1.ocaso.domain.model.Product;
import java.util.List;

public interface ProductRepository {
    List<Product> findByClient(String clientId);
    Product save(Product product);
}
