package io.fulfillx.catalog.api;

import io.fulfillx.catalog.api.dto.CreateProductRequest;
import io.fulfillx.catalog.api.dto.ProductPageResponse;
import io.fulfillx.catalog.api.dto.ProductResponse;
import io.fulfillx.catalog.api.dto.UpdateProductRequest;
import io.fulfillx.catalog.application.CatalogApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final CatalogApplicationService service;

    public ProductController(CatalogApplicationService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "List products", description = "Lists products with pagination, active status, and category filters.")
    public ProductPageResponse list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) Boolean active,
                                    @RequestParam(required = false) UUID categoryId) {
        return service.findProducts(page, size, active, categoryId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by id")
    public ProductResponse get(@PathVariable UUID id) { return service.findProduct(id); }

    @PostMapping
    @Operation(summary = "Create a product", security = @SecurityRequirement(name = "fulfillxBearer"))
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse result = service.createProduct(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(result.id()).toUri();
        return ResponseEntity.created(location).body(result);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a product", security = @SecurityRequirement(name = "fulfillxBearer"))
    public ResponseEntity<ProductResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(service.updateProduct(id, request));
    }
}
