package com.apollo.elevator.notification.repository;

import com.apollo.elevator.notification.logs.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
}
