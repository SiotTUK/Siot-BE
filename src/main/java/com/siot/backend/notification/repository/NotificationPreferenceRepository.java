package com.siot.backend.notification.repository;

import com.siot.backend.notification.domain.NotificationPreference;
import com.siot.backend.notification.domain.NotificationPreferenceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, NotificationPreferenceId> {

    List<NotificationPreference> findByUserId(Long userId);

    Optional<NotificationPreference> findByKindAndUserId(String kind, Long userId);

    void deleteByUserId(Long userId);
}
