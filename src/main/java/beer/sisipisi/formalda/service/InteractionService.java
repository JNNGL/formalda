package beer.sisipisi.formalda.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import beer.sisipisi.formalda.dto.Forms;
import beer.sisipisi.formalda.dto.ViewModels.NotificationView;
import beer.sisipisi.formalda.dto.ViewModels.TopicSummary;
import beer.sisipisi.formalda.entity.Bookmark;
import beer.sisipisi.formalda.entity.Notification;
import beer.sisipisi.formalda.entity.NotificationType;
import beer.sisipisi.formalda.entity.Post;
import beer.sisipisi.formalda.entity.PostLike;
import beer.sisipisi.formalda.entity.PostStatus;
import beer.sisipisi.formalda.entity.Report;
import beer.sisipisi.formalda.entity.Subscription;
import beer.sisipisi.formalda.entity.Topic;
import beer.sisipisi.formalda.entity.User;
import beer.sisipisi.formalda.exception.BusinessRuleException;
import beer.sisipisi.formalda.exception.ResourceNotFoundException;
import beer.sisipisi.formalda.repository.BookmarkRepository;
import beer.sisipisi.formalda.repository.NotificationRepository;
import beer.sisipisi.formalda.repository.PostLikeRepository;
import beer.sisipisi.formalda.repository.PostRepository;
import beer.sisipisi.formalda.repository.ReportRepository;
import beer.sisipisi.formalda.repository.SubscriptionRepository;
import beer.sisipisi.formalda.repository.TopicRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InteractionService {
    private static final int MAX_PAGE = 10_000;

    private final AuthorizationService authorization;
    private final ViewMapper views;
    private final PostRepository posts;
    private final TopicRepository topics;
    private final PostLikeRepository likes;
    private final BookmarkRepository bookmarks;
    private final SubscriptionRepository subscriptions;
    private final NotificationRepository notifications;
    private final ReportRepository reports;
    private final Clock clock;

    public InteractionService(
            AuthorizationService authorization,
            ViewMapper views,
            PostRepository posts,
            TopicRepository topics,
            PostLikeRepository likes,
            BookmarkRepository bookmarks,
            SubscriptionRepository subscriptions,
            NotificationRepository notifications,
            ReportRepository reports,
            Clock clock) {
        this.authorization = authorization;
        this.views = views;
        this.posts = posts;
        this.topics = topics;
        this.likes = likes;
        this.bookmarks = bookmarks;
        this.subscriptions = subscriptions;
        this.notifications = notifications;
        this.reports = reports;
        this.clock = clock;
    }

    @Transactional
    public boolean toggleLike(Long userId, Long postId) {
        User user = authorization.requireUser(userId);
        Long topicId = posts.findTopicIdById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        Topic topic = topics.findByIdForUpdate(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found"));
        Post post = posts.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        authorization.requireTopicWritable(topic);
        if (post.getStatus() != PostStatus.VISIBLE) {
            throw new BusinessRuleException("post.notVisible");
        }
        var existing = likes.findByPostIdAndUserId(postId, userId);
        if (existing.isPresent()) {
            likes.delete(existing.get());
            post.removeLike();
            if (post.isInitialPost()) {
                topic.removeLike();
            }
            return false;
        }
        likes.save(new PostLike(post, user));
        post.registerLike();
        if (post.isInitialPost()) {
            topic.registerLike();
        }
        return true;
    }

    @Transactional
    public boolean toggleBookmark(Long userId, Long topicId) {
        Topic topic = requireTopicForUpdate(topicId);
        User user = authorization.requireUser(userId);
        var existing = bookmarks.findByUserIdAndTopicId(userId, topicId);
        if (existing.isPresent()) {
            bookmarks.delete(existing.get());
            return false;
        }
        authorization.requireTopicWritable(topic);
        bookmarks.save(new Bookmark(user, topic));
        return true;
    }

    @Transactional
    public boolean toggleSubscription(Long userId, Long topicId) {
        Topic topic = requireTopicForUpdate(topicId);
        User user = authorization.requireUser(userId);
        var existing = subscriptions.findByUserIdAndTopicId(userId, topicId);
        if (existing.isPresent()) {
            subscriptions.delete(existing.get());
            return false;
        }
        authorization.requireTopicWritable(topic);
        subscriptions.save(new Subscription(user, topic));
        return true;
    }

    @Transactional(readOnly = true)
    public Page<TopicSummary> bookmarks(Long userId, int page) {
        return bookmarks.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(safePage(page), 20))
                .map(Bookmark::getTopic)
                .map(views::topic);
    }

    @Transactional(readOnly = true)
    public Page<TopicSummary> subscriptions(Long userId, int page) {
        return subscriptions.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(safePage(page), 20))
                .map(Subscription::getTopic)
                .map(views::topic);
    }

    @Transactional(readOnly = true)
    public Page<NotificationView> notifications(Long userId, int page) {
        return notifications.findByRecipientIdOrderByCreatedAtDesc(userId, PageRequest.of(safePage(page), 20))
                .map(views::notification);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByRecipientIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markNotificationRead(Long userId, Long notificationId) {
        notifications.markRead(notificationId, userId, Instant.now(clock));
    }

    @Transactional
    public void markAllNotificationsRead(Long userId) {
        notifications.markAllRead(userId, Instant.now(clock));
    }

    @Transactional
    public void createReport(Long userId, Forms.Report form) {
        User reporter = authorization.requireUser(userId);
        Post post = requirePost(form.getPostId());
        if (post.getStatus() != PostStatus.VISIBLE) {
            throw new BusinessRuleException("post.notVisible");
        }
        if (post.getAuthor().getId().equals(userId)) {
            throw new BusinessRuleException("report.ownPost");
        }
        reports.save(new Report(reporter, post, form.getReason().strip()));
    }

    @Transactional
    public void notifyAboutReply(Post reply) {
        Map<Long, NotificationType> recipients = new LinkedHashMap<>();
        Long actorId = reply.getAuthor().getId();
        for (User subscriber : subscriptions.findSubscribersExcluding(reply.getTopic().getId(), actorId)) {
            recipients.put(subscriber.getId(), NotificationType.NEW_TOPIC_REPLY);
        }
        User topicAuthor = reply.getTopic().getAuthor();
        if (!topicAuthor.getId().equals(actorId)) {
            recipients.put(topicAuthor.getId(), NotificationType.DIRECT_REPLY);
        }
        if (reply.getParentPost() != null && !reply.getParentPost().getAuthor().getId().equals(actorId)) {
            recipients.put(reply.getParentPost().getAuthor().getId(), NotificationType.DIRECT_REPLY);
        }
        recipients.forEach((recipientId, type) -> {
            User recipient = authorization.requireUser(recipientId);
            notifications.save(new Notification(recipient, reply.getAuthor(), reply.getTopic(), reply, type));
        });
    }

    private Post requirePost(Long id) {
        return posts.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    }

    private Topic requireTopicForUpdate(Long id) {
        return topics.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found"));
    }

    private int safePage(int page) {
        return Math.min(MAX_PAGE, Math.max(0, page));
    }
}
