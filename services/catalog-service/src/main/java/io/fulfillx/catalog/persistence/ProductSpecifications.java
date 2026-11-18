package io.fulfillx.catalog.persistence;

import io.fulfillx.catalog.domain.Product;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> matching(Boolean active, UUID categoryId) {
        return (root, query, criteriaBuilder) -> {
            var predicates = criteriaBuilder.conjunction();
            if (active != null) {
                predicates = criteriaBuilder.and(
                        predicates,
                        criteriaBuilder.equal(root.get("active"), active)
                );
            }
            if (categoryId != null) {
                predicates = criteriaBuilder.and(
                        predicates,
                        criteriaBuilder.equal(root.get("category").get("id"), categoryId)
                );
            }
            return predicates;
        };
    }
}
