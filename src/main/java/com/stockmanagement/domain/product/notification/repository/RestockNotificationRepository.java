package com.stockmanagement.domain.product.notification.repository;

import com.stockmanagement.domain.product.notification.entity.RestockNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RestockNotificationRepository extends JpaRepository<RestockNotification, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    List<RestockNotification> findByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserIdAndProductId(Long userId, Long productId);

    @Query("SELECT rn.userId FROM RestockNotification rn WHERE rn.productId = :productId")
    List<Long> findUserIdsByProductId(@Param("productId") Long productId);

    void deleteByProductId(Long productId);

    void deleteByUserId(Long userId);
}
