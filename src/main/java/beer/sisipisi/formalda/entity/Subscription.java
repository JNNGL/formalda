package beer.sisipisi.formalda.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;

@Entity
@Table(name = "subscriptions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subscriptions_user_topic", columnNames = {"user_id", "topic_id"})
})
public class Subscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    protected Subscription() {
    }

    public Subscription(User user, Topic topic) {
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
