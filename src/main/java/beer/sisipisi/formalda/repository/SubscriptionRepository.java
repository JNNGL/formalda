package beer.sisipisi.formalda.repository;

import beer.sisipisi.formalda.entity.Subscription;
import beer.sisipisi.formalda.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByUserIdAndTopicId(Long userId, Long topicId);

    Optional<Subscription> findByUserIdAndTopicId(Long userId, Long topicId);

    Page<Subscription> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Subscription> findByTopicId(Long topicId);

    @Query("""
            select subscription.user from Subscription subscription
             where subscription.topic.id = :topicId
               and subscription.user.id <> :excludedUserId
            """)
    List<User> findSubscribersExcluding(@Param("topicId") Long topicId,
                                        @Param("excludedUserId") Long excludedUserId);

    long deleteByUserIdAndTopicId(Long userId, Long topicId);
}
