package beer.sisipisi.formalda.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, length = 254, unique = true)
    private String email;

    @Column(nullable = false, length = 50, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "blocked_permanently", nullable = false)
    private boolean blockedPermanently;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "block_reason", length = 1000)
    private String blockReason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    protected User() {
    }

    public User(String email, String username, String passwordHash) {
        this.email = Objects.requireNonNull(email);
        this.username = Objects.requireNonNull(username);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.roles.add(Role.USER);
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isBlockedPermanently() {
        return blockedPermanently;
    }

    public Instant getBlockedUntil() {
        return blockedUntil;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean isBlockedAt(Instant instant) {
        return blockedPermanently || (blockedUntil != null && blockedUntil.isAfter(instant));
    }

    public void verifyEmail() {
        emailVerified = true;
    }

    public void changeEmail(String email) {
        this.email = Objects.requireNonNull(email);
        emailVerified = false;
    }

    public void changeUsername(String username) {
        this.username = Objects.requireNonNull(username);
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void addRole(Role role) {
        roles.add(Objects.requireNonNull(role));
    }

    public void removeRole(Role role) {
        roles.remove(role);
    }

    public void blockUntil(Instant until, String reason) {
        blockedPermanently = false;
        blockedUntil = Objects.requireNonNull(until);
        blockReason = reason;
    }

    public void blockPermanently(String reason) {
        blockedPermanently = true;
        blockedUntil = null;
        blockReason = reason;
    }

    public void unblock() {
        blockedPermanently = false;
        blockedUntil = null;
        blockReason = null;
    }
}
