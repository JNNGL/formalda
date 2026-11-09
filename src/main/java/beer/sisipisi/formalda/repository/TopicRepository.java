package beer.sisipisi.formalda.repository;

import beer.sisipisi.formalda.entity.PostStatus;
import beer.sisipisi.formalda.entity.Topic;
import beer.sisipisi.formalda.entity.TopicStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Page<Topic> findByCategoryIdAndStatus(Long categoryId, TopicStatus status, Pageable pageable);

    Page<Topic> findByCategoryIdAndStatusNot(Long categoryId, TopicStatus excludedStatus, Pageable pageable);

    Page<Topic> findByCategoryIdAndStatusNotOrderByPinnedDescLastActivityAtDesc(
            Long categoryId, TopicStatus excludedStatus, Pageable pageable);

    Page<Topic> findByCategoryIdAndStatusNotOrderByLastActivityAtDesc(
            Long categoryId, TopicStatus excludedStatus, Pageable pageable);

    Page<Topic> findByCategoryIdAndStatusOrderByPinnedDescLastActivityAtDesc(
            Long categoryId, TopicStatus status, Pageable pageable);

    Page<Topic> findByCategoryIdAndStatusNotOrderByPinnedDescCreatedAtDesc(
            Long categoryId, TopicStatus excludedStatus, Pageable pageable);

    Page<Topic> findByCategoryIdAndStatusNotOrderByPinnedDescReplyCountDescLikeCountDescLastActivityAtDesc(
            Long categoryId, TopicStatus excludedStatus, Pageable pageable);

    Page<Topic> findByStatusNotAndCategoryEnabledTrueOrderByLastActivityAtDesc(
            TopicStatus excludedStatus, Pageable pageable);

    Page<Topic> findByAuthorIdOrderByCreatedAtDesc(Long authorId, Pageable pageable);

    Page<Topic> findByAuthorIdAndStatusNotAndCategoryEnabledTrueOrderByCreatedAtDesc(
            Long authorId, TopicStatus excludedStatus, Pageable pageable);

    long countByCategoryIdAndStatusNot(Long categoryId, TopicStatus excludedStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select topic from Topic topic where topic.id = :id")
    Optional<Topic> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Topic topic set topic.viewCount = topic.viewCount + 1 where topic.id = :id")
    int incrementViewCount(@Param("id") Long id);

    @Query("""
            select topic from Topic topic
             where topic.category.id = :categoryId
               and ((:status is null and topic.status <> :archivedStatus) or topic.status = :status)
             order by topic.pinned desc,
                      (topic.replyCount + topic.likeCount) desc,
                      topic.lastActivityAt desc
            """)
    Page<Topic> findPopular(@Param("categoryId") Long categoryId,
                            @Param("status") TopicStatus status,
                            @Param("archivedStatus") TopicStatus archivedStatus,
                            Pageable pageable);

    @Query("""
            select topic from Topic topic
             where topic.status <> :archivedStatus
               and topic.category.enabled = true
               and (:categoryId is null or topic.category.id = :categoryId)
               and (
                    lower(topic.title) like lower(concat('%', :query, '%'))
                    or exists (
                        select post.id from Post post
                         where post.topic = topic
                           and post.initialPost = true
                           and post.status = :visibleStatus
                           and lower(post.body) like lower(concat('%', :query, '%'))
                    )
               )
             order by topic.pinned desc, topic.lastActivityAt desc
            """)
    Page<Topic> search(@Param("query") String query,
                       @Param("categoryId") Long categoryId,
                       @Param("archivedStatus") TopicStatus archivedStatus,
                       @Param("visibleStatus") PostStatus visibleStatus,
                       Pageable pageable);
}
