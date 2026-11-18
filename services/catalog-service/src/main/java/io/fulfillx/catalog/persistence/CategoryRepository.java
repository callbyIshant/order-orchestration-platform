package io.fulfillx.catalog.persistence;

import io.fulfillx.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
}
