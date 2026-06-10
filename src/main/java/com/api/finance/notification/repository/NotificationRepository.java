package com.api.finance.notification.repository;

import com.api.finance.notification.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByLidaAscCriadoEmDesc(UUID userId, Pageable pageable);

    List<Notification> findByUserIdAndLidaFalseOrderByCriadoEmDesc(UUID userId);

    long countByUserIdAndLidaFalse(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.lida = true WHERE n.userId = :userId AND n.lida = false")
    int marcarTodasComoLidas(@Param("userId") UUID userId);
}
