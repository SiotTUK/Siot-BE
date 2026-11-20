package com.siot.backend.notification.repository;

import com.siot.backend.notification.domain.EmailDeliveryStatus;
import com.siot.backend.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :readAt WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllAsReadByUserId(@Param("userId") Long userId, @Param("readAt") OffsetDateTime readAt);

    boolean existsByUserIdAndSourceKey(Long userId, String sourceKey);

    @Query("SELECT n FROM Notification n WHERE n.status = :status AND n.scheduledAt <= :currentTime ORDER BY n.scheduledAt ASC")
    List<Notification> findPendingNotifications(
            @Param("status") String status,
            @Param("currentTime") OffsetDateTime currentTime,
            Pageable pageable
    );

    @Query("SELECT n FROM Notification n WHERE n.emailStatus = :emailStatus AND n.scheduledAt <= :currentTime ORDER BY n.scheduledAt ASC")
    List<Notification> findPendingEmailNotifications(
            @Param("emailStatus") EmailDeliveryStatus emailStatus,
            @Param("currentTime") OffsetDateTime currentTime,
            Pageable pageable
    );

    void deleteByUserId(Long userId);
}
