package beer.sisipisi.formalda.repository;

import beer.sisipisi.formalda.entity.ModeratorCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModeratorCategoryRepository extends JpaRepository<ModeratorCategory, Long> {

    boolean existsByModeratorIdAndCategoryId(Long moderatorId, Long categoryId);

    Optional<ModeratorCategory> findByModeratorIdAndCategoryId(Long moderatorId, Long categoryId);

    List<ModeratorCategory> findByModeratorIdOrderByCategorySortOrderAsc(Long moderatorId);

    List<ModeratorCategory> findByCategoryId(Long categoryId);

    long deleteByModeratorIdAndCategoryId(Long moderatorId, Long categoryId);
}
