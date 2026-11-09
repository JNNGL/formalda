package beer.sisipisi.formalda.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;

@Entity
@Table(name = "moderator_categories", uniqueConstraints = {
        @UniqueConstraint(name = "uk_moderator_categories_moderator_category",
                columnNames = {"moderator_id", "category_id"})
})
public class ModeratorCategory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moderator_id", nullable = false)
    private User moderator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_id")
    private User assignedBy;

    protected ModeratorCategory() {
    }

    public ModeratorCategory(User moderator, Category category, User assignedBy) {
        this.moderator = Objects.requireNonNull(moderator);
        this.category = Objects.requireNonNull(category);
        this.assignedBy = assignedBy;
    }

    public User getModerator() {
        return moderator;
    }

    public Category getCategory() {
        return category;
    }

    public User getAssignedBy() {
        return assignedBy;
    }
}
