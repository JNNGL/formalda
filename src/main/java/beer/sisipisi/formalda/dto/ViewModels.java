package beer.sisipisi.formalda.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import beer.sisipisi.formalda.entity.NotificationType;
import beer.sisipisi.formalda.entity.PostStatus;
import beer.sisipisi.formalda.entity.ReportStatus;
import beer.sisipisi.formalda.entity.Role;
import beer.sisipisi.formalda.entity.TopicStatus;
import org.springframework.data.domain.Page;

public final class ViewModels {
    private ViewModels() {
    }

    public record CategorySummary(
            Long id, String slug, String name, String description, int sortOrder,
            boolean enabled, long topicCount, Instant lastActivityAt) {
    }

    public record CategoryOption(Long id, String name) {
    }

    public record CategoryDetail(
            Long id, String slug, String name, String description, boolean enabled) {
    }

    public record AdminCategoryView(
            Long id, String slug, String name, String description, int sortOrder,
            boolean enabled, Instant createdAt, Instant updatedAt) {
    }

    public record AdminUserView(
            Long id, String email, String username, boolean emailVerified,
            boolean blocked, boolean blockedPermanently, Instant blockedUntil, Set<Role> roles,
            Instant createdAt) {
        public AdminUserView {
            roles = Set.copyOf(roles);
        }
    }

    public enum TopicStatusFilter { OPEN, CLOSED, ARCHIVED }

    public enum TopicSort { UPDATED, NEW, POPULAR }

    public record TopicSummary(
            Long id, Long categoryId, String categoryName, boolean categoryEnabled,
            String title, String authorUsername,
            TopicStatus status, boolean pinned, long replyCount, long likeCount, long viewCount,
            Instant createdAt, Instant lastActivityAt, List<String> tags) {
    }

    public record PostView(
            Long id, String authorUsername, String body, PostStatus status, boolean initialPost,
            Long parentPostId, String parentAuthorUsername, long likeCount, boolean liked,
            boolean canEdit, boolean canDelete, Instant createdAt, Instant editedAt) {
    }

    public record PostLocation(Long topicId, Long postId, int page) {
        public String url() {
            return "/topics/" + topicId + "?page=" + page + "#post-" + postId;
        }

        public String replyUrl() {
            return "/topics/" + topicId + "?page=" + page + "&replyTo=" + postId + "#post-" + postId;
        }
    }

    public record TopicPage(
            TopicSummary topic, Page<PostView> posts, boolean bookmarked, boolean subscribed,
            boolean canReply, boolean canEditTopic, boolean canClose, boolean canReopen,
            boolean canModerate) {
    }

    public record UserProfile(
            Long id, String username, Instant joinedAt, boolean blocked, String blockReason,
            Set<Role> roles, Page<TopicSummary> topics, Page<PostView> posts) {
    }

    public record NotificationView(
            Long id, NotificationType type, String actorUsername, Long topicId, String topicTitle,
            Long postId, boolean read, Instant createdAt) {
    }

    public record ReportView(
            Long id, String reporterUsername, Long postId, String postAuthorUsername,
            Long topicId, String topicTitle, String categoryName, String reason,
            ReportStatus status, String resolutionReason, String resolvedByUsername,
            Instant createdAt, Instant resolvedAt) {
    }
}
