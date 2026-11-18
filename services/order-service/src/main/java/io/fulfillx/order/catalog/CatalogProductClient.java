package io.fulfillx.order.catalog;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class CatalogProductClient {
    private final RestClient restClient;
    public CatalogProductClient(RestClient restClient) { this.restClient = restClient; }

    public CatalogProduct getOrderableProduct(UUID productId) {
        try {
            CatalogProduct product = restClient.get().uri("/api/v1/products/{id}", productId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        if (response.getStatusCode().value() == 404) throw new CatalogClientException(CatalogClientException.Kind.NOT_ORDERABLE, "Product is not in the Catalog.", null);
                        throw new CatalogClientException(CatalogClientException.Kind.UNAVAILABLE, "Catalog rejected the product lookup.", null);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> { throw new CatalogClientException(CatalogClientException.Kind.UNAVAILABLE, "Catalog is unavailable.", null); })
                    .body(CatalogProduct.class);
            if (product == null || !product.active() || !"INR".equals(product.currency())) {
                throw new CatalogClientException(CatalogClientException.Kind.NOT_ORDERABLE, "Product is inactive or has an unsupported currency.", null);
            }
            return product;
        } catch (CatalogClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new CatalogClientException(CatalogClientException.Kind.UNAVAILABLE, "Catalog lookup failed.", exception);
        }
    }
}
