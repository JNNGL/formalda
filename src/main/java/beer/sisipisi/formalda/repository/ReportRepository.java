package beer.sisipisi.formalda.repository;

import beer.sisipisi.formalda.entity.Report;
import beer.sisipisi.formalda.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query("select report.post.id from Report report where report.id = :id")
    Optional<Long> findPostIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from Report report where report.id = :id")
    Optional<Report> findByIdForUpdate(@Param("id") Long id);

    Page<Report> findByStatusOrderByCreatedAtAsc(ReportStatus status, Pageable pageable);

    Page<Report> findByReporterIdOrderByCreatedAtDesc(Long reporterId, Pageable pageable);

    @Query("""
            select report from Report report
             where report.status = :status
               and exists (
                    select assignment.id from ModeratorCategory assignment
                     where assignment.moderator.id = :moderatorId
                       and assignment.category = report.post.topic.category
               )
             order by report.createdAt asc
            """)
    Page<Report> findModerationQueue(@Param("moderatorId") Long moderatorId,
                                     @Param("status") ReportStatus status,
                                     Pageable pageable);
}
