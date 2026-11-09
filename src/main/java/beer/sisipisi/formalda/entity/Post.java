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
@Table(name = "posts")
public class Post extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_post_id")
    private Post parentPost;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PostStatus status = PostStatus.VISIBLE;

    @Column(name = "initial_post", nullable = false)
    private boolean initialPost;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removal_reason", length = 1000)
    private String removalReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "removed_by_id")
    private User removedBy;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    protected Post() {
    }

    public Post(Topic topic, User author, String body, boolean initialPost, Post parentPost) {
        this.topic = Objects.requireNonNull(topic);
        this.author = Objects.requireNonNull(author);
        this.body = Objects.requireNonNull(body);
        this.initialPost = initialPost;
        this.parentPost = parentPost;
    }

    public Topic getTopic() {
        return topic;
    }

    public User getAuthor() {
        return author;
    }

    public Post getParentPost() {
        return parentPost;
    }

    public String getBody() {
        return body;
    }

    public PostStatus getStatus() {
        return status;
    }

    public boolean isInitialPost() {
        return initialPost;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public String getRemovalReason() {
        return removalReason;
    }

    public User getRemovedBy() {
        return removedBy;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void edit(String body) {
        this.body = Objects.requireNonNull(body);
        editedAt = Instant.now();
    }

    public void softDelete(User removedBy, String reason) {
        status = PostStatus.DELETED;
        removedAt = Instant.now();
        this.removedBy = removedBy;
        removalReason = reason;
    }

    public void hide(User moderator, String reason) {
        status = PostStatus.HIDDEN;
        removedAt = Instant.now();
        removedBy = Objects.requireNonNull(moderator);
        removalReason = reason;
    }

    public void registerLike() {
        likeCount++;
    }

    public void removeLike() {
        if (likeCount > 0) {
            likeCount--;
        }
    }
}
