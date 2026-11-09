package beer.sisipisi.formalda.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;

@Entity
@Table(name = "bookmarks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_bookmarks_user_topic", columnNames = {"user_id", "topic_id"})
})
public class Bookmark extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    protected Bookmark() {
    }

    public Bookmark(User user, Topic topic) {
        this.user = Objects.requireNonNull(user);
        this.topic = Objects.requireNonNull(topic);
    }

    public User getUser() {
        return user;
    }

    public Topic getTopic() {
        return topic;
    }
}
