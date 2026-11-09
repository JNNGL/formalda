package beer.sisipisi.formalda.repository;

import beer.sisipisi.formalda.entity.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByUserIdAndTopicId(Long userId, Long topicId);

    Optional<Bookmark> findByUserIdAndTopicId(Long userId, Long topicId);

    Page<Bookmark> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long deleteByUserIdAndTopicId(Long userId, Long topicId);
}
