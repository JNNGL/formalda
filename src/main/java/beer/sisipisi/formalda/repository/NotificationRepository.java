package beer.sisipisi.formalda.repository;

import beer.sisipisi.formalda.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification notification
               set notification.readAt = :now, notification.updatedAt = :now
             where notification.id = :notificationId
               and notification.recipient.id = :recipientId
               and notification.readAt is null
            """)
    int markRead(@Param("notificationId") Long notificationId,
                 @Param("recipientId") Long recipientId,
                 @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification notification
               set notification.readAt = :now, notification.updatedAt = :now
             where notification.recipient.id = :recipientId
               and notification.readAt is null
            """)
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") Instant now);
}
