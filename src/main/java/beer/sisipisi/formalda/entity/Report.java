package beer.sisipisi.formalda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "reports")
public class Report extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReportStatus status = ReportStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    @Column(name = "resolution_reason", length = 1000)
    private String resolutionReason;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Report() {
    }

    public Report(User reporter, Post post, String reason) {
        this.reporter = Objects.requireNonNull(reporter);
        this.post = Objects.requireNonNull(post);
        this.reason = Objects.requireNonNull(reason);
    }

    public User getReporter() {
        return reporter;
    }

    public Post getPost() {
        return post;
    }

    public String getReason() {
        return reason;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public User getResolvedBy() {
        return resolvedBy;
    }

    public String getResolutionReason() {
        return resolutionReason;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void resolve(ReportStatus status, User moderator, String resolutionReason) {
        if (status == ReportStatus.PENDING) {
            throw new IllegalArgumentException("A resolved report cannot remain pending");
        }
        this.status = Objects.requireNonNull(status);
        this.resolvedBy = Objects.requireNonNull(moderator);
        this.resolutionReason = resolutionReason;
        this.resolvedAt = Instant.now();
    }
}
