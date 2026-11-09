package beer.sisipisi.formalda.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;

@Entity
@Table(name = "post_likes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_post_likes_post_user", columnNames = {"post_id", "user_id"})
})
public class PostLike extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected PostLike() {
    }

    public PostLike(Post post, User user) {
        this.post = Objects.requireNonNull(post);
        this.user = Objects.requireNonNull(user);
    }

    public Post getPost() {
        return post;
    }

    public User getUser() {
        return user;
    }
}
