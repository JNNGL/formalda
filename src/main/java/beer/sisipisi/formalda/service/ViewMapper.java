package beer.sisipisi.formalda.service;

import java.time.Clock;
import java.time.Instant;
import beer.sisipisi.formalda.dto.ViewModels.AdminCategoryView;
import beer.sisipisi.formalda.dto.ViewModels.AdminUserView;
import beer.sisipisi.formalda.dto.ViewModels.CategoryDetail;
import beer.sisipisi.formalda.dto.ViewModels.CategoryOption;
import beer.sisipisi.formalda.dto.ViewModels.NotificationView;
import beer.sisipisi.formalda.dto.ViewModels.PostView;
import beer.sisipisi.formalda.dto.ViewModels.ReportView;
import beer.sisipisi.formalda.dto.ViewModels.TopicSummary;
import beer.sisipisi.formalda.entity.Category;
import beer.sisipisi.formalda.entity.Notification;
import beer.sisipisi.formalda.entity.Post;
import beer.sisipisi.formalda.entity.Report;
import beer.sisipisi.formalda.entity.Tag;
import beer.sisipisi.formalda.entity.Topic;
import beer.sisipisi.formalda.entity.User;
import org.springframework.stereotype.Component;

@Component
public class ViewMapper {
    private final Clock clock;

    public ViewMapper(Clock clock) {
        this.clock = clock;
    }

    public CategoryOption categoryOption(Category category) {
        return new CategoryOption(category.getId(), category.getName());
    }

    public CategoryDetail categoryDetail(Category category) {
        return new CategoryDetail(
                category.getId(), category.getSlug(), category.getName(),
                category.getDescription(), category.isEnabled());
    }

    public AdminCategoryView adminCategory(Category category) {
        return new AdminCategoryView(
                category.getId(), category.getSlug(), category.getName(), category.getDescription(),
                category.getSortOrder(), category.isEnabled(), category.getCreatedAt(), category.getUpdatedAt());
    }

    public AdminUserView adminUser(User user) {
        return new AdminUserView(
                user.getId(), user.getEmail(), user.getUsername(), user.isEmailVerified(),
                user.isBlockedAt(Instant.now(clock)), user.isBlockedPermanently(), user.getBlockedUntil(),
                user.getRoles(), user.getCreatedAt());
    }

    public TopicSummary topic(Topic topic) {
        return new TopicSummary(
                topic.getId(),
                topic.getCategory().getId(),
                topic.getCategory().getName(),
                topic.getCategory().isEnabled(),
                topic.getTitle(),
                topic.getAuthor().getUsername(),
                topic.getStatus(),
                topic.isPinned(),
                topic.getReplyCount(),
                topic.getLikeCount(),
                topic.getViewCount(),
                topic.getCreatedAt(),
                topic.getLastActivityAt(),
                topic.getTags().stream().map(Tag::getName).sorted().toList());
    }

    public PostView post(Post post, boolean liked, boolean canEdit, boolean canDelete) {
        return new PostView(
                post.getId(),
                post.getAuthor().getUsername(),
                post.getBody(),
                post.getStatus(),
                post.isInitialPost(),
                post.getParentPost() == null ? null : post.getParentPost().getId(),
                post.getParentPost() == null ? null : post.getParentPost().getAuthor().getUsername(),
                post.getLikeCount(),
                liked,
                canEdit,
                canDelete,
                post.getCreatedAt(),
                post.getEditedAt());
    }

    public NotificationView notification(Notification notification) {
        return new NotificationView(
                notification.getId(),
                notification.getType(),
                notification.getActor() == null ? null : notification.getActor().getUsername(),
                notification.getTopic() == null ? null : notification.getTopic().getId(),
                notification.getTopic() == null ? null : notification.getTopic().getTitle(),
                notification.getPost() == null ? null : notification.getPost().getId(),
                notification.isRead(),
                notification.getCreatedAt());
    }

    public ReportView report(Report report) {
        return new ReportView(
                report.getId(),
                report.getReporter().getUsername(),
                report.getPost().getId(),
                report.getPost().getAuthor().getUsername(),
                report.getPost().getTopic().getId(),
                report.getPost().getTopic().getTitle(),
                report.getPost().getTopic().getCategory().getName(),
                report.getReason(),
                report.getStatus(),
                report.getResolutionReason(),
                report.getResolvedBy() == null ? null : report.getResolvedBy().getUsername(),
                report.getCreatedAt(),
                report.getResolvedAt());
    }
}
