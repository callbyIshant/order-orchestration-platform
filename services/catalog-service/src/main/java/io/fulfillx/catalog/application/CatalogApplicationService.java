package io.fulfillx.catalog.application;

import io.fulfillx.catalog.api.dto.CreateProductRequest;
import io.fulfillx.catalog.api.dto.ProductPageResponse;
import io.fulfillx.catalog.api.dto.ProductResponse;
import io.fulfillx.catalog.api.dto.UpdateProductRequest;
import io.fulfillx.catalog.domain.Category;
import io.fulfillx.catalog.domain.CatalogDomainException;
import io.fulfillx.catalog.domain.Product;
import io.fulfillx.catalog.persistence.CategoryRepository;
import io.fulfillx.catalog.persistence.ProductRepository;
import io.fulfillx.catalog.persistence.ProductSpecifications;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CatalogApplicationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public CatalogApplicationService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public ProductPageResponse findProducts(int page, int size, Boolean active, UUID categoryId) {
        if (page < 0) {
            throw new CatalogDomainException("INVALID_PAGINATION", "Page must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new CatalogDomainException("INVALID_PAGINATION", "Page size must be between 1 and 100.");
        }

        Boolean activeFilter = active == null ? Boolean.TRUE : active;
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))
        );
        Page<Product> products = productRepository.findAll(
                ProductSpecifications.matching(activeFilter, categoryId),
                pageable
        );
        return new ProductPageResponse(
                products.getContent().stream().map(ProductResponse::from).toList(),
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ProductResponse findProduct(UUID productId) {
        return productRepository.findById(productId)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        String sku = Product.normalizeSku(request.sku());
        if (productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException(sku);
        }
        Category category = findCategory(request.categoryId());
        Product product = new Product(
                UUID.randomUUID(),
                sku,
                request.name(),
                request.description(),
                request.unitPrice(),
                category,
                request.active() == null || request.active()
        );
        try {
            return ProductResponse.from(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateSkuException(sku);
        }
    }

    @Transactional
    public ProductResponse updateProduct(UUID productId, UpdateProductRequest request) {
        if (!request.hasChanges()) {
            throw new CatalogDomainException("EMPTY_PATCH", "At least one product property must be supplied.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        Category category = request.categoryId() == null ? null : findCategory(request.categoryId());
        product.update(request.name(), request.description(), request.unitPrice(), category, request.active());
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }
}
