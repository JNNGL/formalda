package beer.sisipisi.formalda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "topics")
public class Topic extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 180)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TopicStatus status = TopicStatus.OPEN;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "reply_count", nullable = false)
    private long replyCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_changed_by_id")
    private User statusChangedBy;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @ManyToMany
    @JoinTable(name = "topic_tags",
            joinColumns = @JoinColumn(name = "topic_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();

    protected Topic() {
    }

    public Topic(Category category, User author, String title) {
        this.category = Objects.requireNonNull(category);
        this.author = Objects.requireNonNull(author);
        this.title = Objects.requireNonNull(title);
        this.lastActivityAt = Instant.now();
    }

    public Category getCategory() {
        return category;
    }

    public User getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public TopicStatus getStatus() {
        return status;
    }

    public boolean isPinned() {
        return pinned;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public long getReplyCount() {
        return replyCount;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public User getStatusChangedBy() {
        return statusChangedBy;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Set<Tag> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public void rename(String title) {
        this.title = Objects.requireNonNull(title);
    }

    public void setStatus(TopicStatus status) {
        this.status = Objects.requireNonNull(status);
        this.statusChangedAt = Instant.now();
    }

    public void setStatus(TopicStatus status, User changedBy) {
        setStatus(status);
        this.statusChangedBy = changedBy;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public void registerReply(Instant activityAt) {
        replyCount++;
        touch(activityAt);
    }

    public void removeReply() {
        if (replyCount > 0) {
            replyCount--;
        }
    }

    public void registerLike() {
        likeCount++;
    }

    public void removeLike() {
        if (likeCount > 0) {
            likeCount--;
        }
    }

    public void registerView() {
        viewCount++;
    }

    public void touch(Instant activityAt) {
        lastActivityAt = Objects.requireNonNull(activityAt);
    }

    public void addTag(Tag tag) {
        tags.add(Objects.requireNonNull(tag));
    }

    public void removeTag(Tag tag) {
        tags.remove(tag);
    }
}
