package beer.sisipisi.formalda.dto;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class Forms {
    private Forms() {
    }

    public static final class Registration {
        @NotBlank @Email @Size(max = 254)
        private String email;
        @NotBlank @Size(min = 3, max = 30)
        @Pattern(regexp = "[\\p{L}\\p{N}._-]+")
        private String username;
        @NotBlank @Size(min = 8, max = 72)
        private String password;
        @NotBlank
        private String passwordConfirmation;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getPasswordConfirmation() { return passwordConfirmation; }
        public void setPasswordConfirmation(String passwordConfirmation) { this.passwordConfirmation = passwordConfirmation; }
    }

    public static final class Verification {
        @NotBlank @Email @Size(max = 254)
        private String email;
        @NotBlank @Pattern(regexp = "\\d{6}")
        private String code;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    public static class Topic {
        @NotNull
        private Long categoryId;
        @NotBlank @Size(min = 5, max = 160)
        private String title;
        @NotBlank @Size(min = 10, max = 10_000)
        private String body;
        @Size(max = 200)
        private String tags;

        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
    }

    public static final class Reply {
        @NotBlank @Size(max = 10_000)
        private String body;
        private Long replyToPostId;

        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public Long getReplyToPostId() { return replyToPostId; }
        public void setReplyToPostId(Long replyToPostId) { this.replyToPostId = replyToPostId; }
    }

    public static final class PostEdit {
        @NotBlank @Size(max = 10_000)
        private String body;

        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    public static final class Report {
        @NotNull
        private Long postId;
        @NotBlank @Size(min = 10, max = 1_000)
        private String reason;

        public Long getPostId() { return postId; }
        public void setPostId(Long postId) { this.postId = postId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static final class Profile {
        @NotBlank @Size(min = 3, max = 30)
        @Pattern(regexp = "[\\p{L}\\p{N}._-]+")
        private String username;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }

    public static final class ModerationDecision {
        @NotBlank
        private String action;
        @NotBlank @Size(min = 3, max = 1_000)
        private String reason;
        @Min(1) @Max(3650)
        private Integer blockDays;

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public Integer getBlockDays() { return blockDays; }
        public void setBlockDays(Integer blockDays) { this.blockDays = blockDays; }
    }

    public static final class Category {
        @NotBlank @Size(min = 2, max = 80)
        private String name;
        @NotBlank @Size(max = 500)
        private String description;
        @Min(0)
        private int sortOrder;
        private boolean enabled = true;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static final class UserAdministration {
        private Set<String> roles = new LinkedHashSet<>();
        private Set<Long> moderatedCategoryIds = new LinkedHashSet<>();
        private boolean blocked;
        private LocalDateTime blockedUntil;
        @Size(max = 1_000)
        private String reason;

        public Set<String> getRoles() { return roles; }
        public void setRoles(Set<String> roles) { this.roles = roles; }
        public Set<Long> getModeratedCategoryIds() { return moderatedCategoryIds; }
        public void setModeratedCategoryIds(Set<Long> moderatedCategoryIds) { this.moderatedCategoryIds = moderatedCategoryIds; }
        public boolean isBlocked() { return blocked; }
        public void setBlocked(boolean blocked) { this.blocked = blocked; }
        public LocalDateTime getBlockedUntil() { return blockedUntil; }
        public void setBlockedUntil(LocalDateTime blockedUntil) { this.blockedUntil = blockedUntil; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
