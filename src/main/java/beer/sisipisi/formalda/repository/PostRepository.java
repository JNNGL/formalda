package beer.sisipisi.formalda.repository;

import beer.sisipisi.formalda.entity.Post;
import beer.sisipisi.formalda.entity.PostStatus;
import beer.sisipisi.formalda.entity.TopicStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByTopicIdOrderByCreatedAtAscIdAsc(Long topicId, Pageable pageable);

    Optional<Post> findByTopicIdAndInitialPostTrue(Long topicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select post from Post post where post.id = :id")
    Optional<Post> findByIdForUpdate(@Param("id") Long id);

    @Query("select post.topic.id from Post post where post.id = :id")
    Optional<Long> findTopicIdById(@Param("id") Long id);

    @Query("""
            select count(post.id) from Post post
             where post.topic.id = :topicId
               and (post.createdAt < :createdAt
                    or (post.createdAt = :createdAt and post.id < :postId))
            """)
    long countBefore(@Param("topicId") Long topicId,
                     @Param("createdAt") Instant createdAt,
                     @Param("postId") Long postId);

    Page<Post> findByAuthorIdOrderByCreatedAtDesc(Long authorId, Pageable pageable);

    Page<Post> findByAuthorIdAndInitialPostFalseAndTopicStatusNotAndTopicCategoryEnabledTrueOrderByCreatedAtDesc(
            Long authorId, TopicStatus excludedStatus, Pageable pageable);

    long countByTopicIdAndInitialPostFalseAndStatus(Long topicId, PostStatus status);
}
