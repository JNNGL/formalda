package beer.sisipisi.formalda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "tags")
public class Tag extends BaseEntity {

    @Column(nullable = false, length = 40, unique = true)
    private String name;

    protected Tag() {
    }

    public Tag(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String getName() {
        return name;
    }
}
