package beer.sisipisi.formalda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    @Column(nullable = false, length = 80, unique = true)
    private String slug;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled = true;

    protected Category() {
    }

    public Category(String slug, String name, String description, int sortOrder) {
        this.slug = Objects.requireNonNull(slug);
        this.name = Objects.requireNonNull(name);
        this.description = Objects.requireNonNull(description);
        this.sortOrder = sortOrder;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void update(String slug, String name, String description, int sortOrder) {
        this.slug = Objects.requireNonNull(slug);
        this.name = Objects.requireNonNull(name);
        this.description = Objects.requireNonNull(description);
        this.sortOrder = sortOrder;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
