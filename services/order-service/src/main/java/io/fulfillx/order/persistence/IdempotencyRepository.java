package io.fulfillx.order.persistence;

import io.fulfillx.order.domain.IdempotencyRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {
    @Query("select record from IdempotencyRecord record join fetch record.order where record.customerId = :customer and record.key = :key")
    Optional<IdempotencyRecord> findWithOrder(@Param("customer") String customer, @Param("key") String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from IdempotencyRecord record join fetch record.order where record.customerId = :customer and record.key = :key")
    Optional<IdempotencyRecord> findWithOrderForUpdate(@Param("customer") String customer, @Param("key") String key);
}
