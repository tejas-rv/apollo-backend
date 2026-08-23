package com.apollo.elevators.notification.repository;

import com.apollo.elevators.notification.logs.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
}
