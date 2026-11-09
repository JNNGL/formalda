package beer.sisipisi.formalda.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import beer.sisipisi.formalda.dto.Forms;
import beer.sisipisi.formalda.dto.ViewModels.CategoryDetail;
import beer.sisipisi.formalda.dto.ViewModels.CategoryOption;
import beer.sisipisi.formalda.dto.ViewModels.CategorySummary;
import beer.sisipisi.formalda.dto.ViewModels.PostLocation;
import beer.sisipisi.formalda.dto.ViewModels.PostView;
import beer.sisipisi.formalda.dto.ViewModels.TopicPage;
import beer.sisipisi.formalda.dto.ViewModels.TopicSort;
import beer.sisipisi.formalda.dto.ViewModels.TopicStatusFilter;
import beer.sisipisi.formalda.dto.ViewModels.TopicSummary;
import beer.sisipisi.formalda.dto.ViewModels.UserProfile;
import beer.sisipisi.formalda.entity.Category;
import beer.sisipisi.formalda.entity.Post;
import beer.sisipisi.formalda.entity.PostStatus;
import beer.sisipisi.formalda.entity.Tag;
import beer.sisipisi.formalda.entity.Topic;
import beer.sisipisi.formalda.entity.TopicStatus;
import beer.sisipisi.formalda.entity.User;
import beer.sisipisi.formalda.exception.BusinessRuleException;
import beer.sisipisi.formalda.exception.ResourceNotFoundException;
import beer.sisipisi.formalda.repository.BookmarkRepository;
import beer.sisipisi.formalda.repository.CategoryRepository;
import beer.sisipisi.formalda.repository.PostLikeRepository;
import beer.sisipisi.formalda.repository.PostRepository;
import beer.sisipisi.formalda.repository.SubscriptionRepository;
import beer.sisipisi.formalda.repository.TagRepository;
import beer.sisipisi.formalda.repository.TopicRepository;
import beer.sisipisi.formalda.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForumService {
    private static final int POSTS_PER_PAGE = 25;
    private static final int MAX_PAGE = 10_000;

    private final CategoryRepository categories;
    private final TopicRepository topics;
    private final PostRepository posts;
    private final TagRepository tags;
    private final UserRepository users;
    private final PostLikeRepository likes;
    private final BookmarkRepository bookmarks;
    private final SubscriptionRepository subscriptions;
    private final AuthorizationService authorization;
    private final InteractionService interactions;
    private final ViewMapper views;
    private final Clock clock;

    public ForumService(
            CategoryRepository categories,
            TopicRepository topics,
            PostRepository posts,
            TagRepository tags,
            UserRepository users,
            PostLikeRepository likes,
            BookmarkRepository bookmarks,
            SubscriptionRepository subscriptions,
            AuthorizationService authorization,
            InteractionService interactions,
            ViewMapper views,
            Clock clock) {
        this.categories = categories;
        this.topics = topics;
        this.posts = posts;
        this.tags = tags;
        this.users = users;
        this.likes = likes;
        this.bookmarks = bookmarks;
        this.subscriptions = subscriptions;
        this.authorization = authorization;
        this.interactions = interactions;
        this.views = views;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CategorySummary> homeCategories() {
        List<CategorySummary> result = new ArrayList<>();
        for (Category category : categories.findAllByEnabledTrueOrderBySortOrderAscNameAsc()) {
            Page<Topic> latest = topics.findByCategoryIdAndStatusNotOrderByLastActivityAtDesc(
                    category.getId(), TopicStatus.ARCHIVED, PageRequest.of(0, 1));
            result.add(new CategorySummary(
                    category.getId(), category.getSlug(), category.getName(), category.getDescription(),
                    category.getSortOrder(), category.isEnabled(),
                    topics.countByCategoryIdAndStatusNot(category.getId(), TopicStatus.ARCHIVED),
                    latest.isEmpty() ? null : latest.getContent().getFirst().getLastActivityAt()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<TopicSummary> recentTopics() {
        return topics.findByStatusNotAndCategoryEnabledTrueOrderByLastActivityAtDesc(
                        TopicStatus.ARCHIVED, PageRequest.of(0, 8))
                .stream().map(views::topic).toList();
    }

    @Transactional(readOnly = true)
    public CategoryDetail categoryDetail(Long id) {
        return views.categoryDetail(requireCategory(id));
    }

    @Transactional(readOnly = true)
    public List<CategoryOption> enabledCategoryOptions() {
        return categories.findAllByEnabledTrueOrderBySortOrderAscNameAsc().stream()
                .map(views::categoryOption)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TopicSummary> categoryTopics(
            Long categoryId, TopicStatusFilter status, TopicSort sort, int page) {
        requireCategory(categoryId);
        TopicStatus topicStatus = status == null ? null : TopicStatus.valueOf(status.name());
        TopicSort effectiveSort = sort == null ? TopicSort.UPDATED : sort;
        Sort ordering = switch (effectiveSort) {
            case UPDATED -> Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("lastActivityAt"));
            case NEW -> Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createdAt"));
            case POPULAR -> Sort.unsorted();
        };
        Pageable pageable = PageRequest.of(safePage(page), 20, ordering);
        Page<Topic> result;
        if (effectiveSort == TopicSort.POPULAR) {
            result = topics.findPopular(categoryId, topicStatus, TopicStatus.ARCHIVED, pageable);
        } else if (topicStatus != null) {
            result = topics.findByCategoryIdAndStatus(categoryId, topicStatus, pageable);
        } else {
            result = topics.findByCategoryIdAndStatusNot(categoryId, TopicStatus.ARCHIVED, pageable);
        }
        return result.map(views::topic);
    }

    @Transactional
    public TopicPage topic(Long topicId, int page, Long viewerId) {
        if (topics.incrementViewCount(topicId) == 0) {
            throw new ResourceNotFoundException("Topic not found");
        }
        Topic topic = requireTopic(topicId);
        User viewer = viewerId == null ? null : authorization.requireUser(viewerId);
        Page<PostView> postViews = posts.findByTopicIdOrderByCreatedAtAscIdAsc(
                        topicId, PageRequest.of(safePage(page), POSTS_PER_PAGE))
                .map(post -> toPostView(post, viewer));
        boolean initialPostVisible = initialPost(topicId).getStatus() == PostStatus.VISIBLE;
        boolean moderator = viewer != null && authorization.canModerate(viewer, topic.getCategory());
        boolean owner = viewer != null && authorization.owns(viewer, topic);
        boolean blocked = viewer != null && viewer.isBlockedAt(Instant.now(clock));
        boolean writable = topic.getCategory().isEnabled() && topic.getStatus() != TopicStatus.ARCHIVED;
        return new TopicPage(
                views.topic(topic),
                postViews,
                viewer != null && bookmarks.existsByUserIdAndTopicId(viewer.getId(), topicId),
                viewer != null && subscriptions.existsByUserIdAndTopicId(viewer.getId(), topicId),
                viewer != null && !blocked && writable && topic.getStatus() == TopicStatus.OPEN,
                owner && !blocked && writable && initialPostVisible,
                writable && (moderator || (owner && !blocked)) && topic.getStatus() == TopicStatus.OPEN,
                writable && moderator && topic.getStatus() == TopicStatus.CLOSED,
                writable && moderator);
    }

    @Transactional
    public Long createTopic(Long userId, Forms.Topic form) {
        User author = authorization.requireUser(userId);
        authorization.requireCanPublish(author);
        Category category = requireCategory(form.getCategoryId());
        if (!category.isEnabled()) {
            throw new BusinessRuleException("category.disabled");
        }
        Topic topic = topics.save(new Topic(category, author, form.getTitle().strip()));
        applyTags(topic, form.getTags());
        posts.save(new Post(topic, author, form.getBody().strip(), true, null));
        return topic.getId();
    }

    @Transactional(readOnly = true)
    public Forms.Topic topicEditForm(Long userId, Long topicId) {
        User user = authorization.requireUser(userId);
        authorization.requireCanPublish(user);
        Topic topic = requireTopic(topicId);
        if (!authorization.owns(user, topic)) {
            throw new org.springframework.security.access.AccessDeniedException("Only the author can edit the topic");
        }
        authorization.requireTopicWritable(topic);
        Post initial = initialPost(topicId);
        requirePostEditable(initial);
        Forms.Topic form = new Forms.Topic();
        form.setCategoryId(topic.getCategory().getId());
        form.setTitle(topic.getTitle());
        form.setBody(initial.getBody());
        form.setTags(String.join(", ", topic.getTags().stream().map(Tag::getName).toList()));
        return form;
    }

    @Transactional
    public void editTopic(Long userId, Long topicId, Forms.Topic form) {
        User user = authorization.requireUser(userId);
        authorization.requireCanPublish(user);
        Topic topic = requireTopicForUpdate(topicId);
        if (!authorization.owns(user, topic)) {
            throw new org.springframework.security.access.AccessDeniedException("Only the author can edit the topic");
        }
        authorization.requireTopicWritable(topic);
        Post initial = initialPost(topicId);
        requirePostEditable(initial);
        topic.rename(form.getTitle().strip());
        initial.edit(form.getBody().strip());
        applyTags(topic, form.getTags());
        topic.touch(Instant.now(clock));
    }

    @Transactional
    public Long createReply(Long userId, Long topicId, Forms.Reply form) {
        User author = authorization.requireUser(userId);
        authorization.requireCanPublish(author);
        Topic topic = requireTopicForUpdate(topicId);
        authorization.requireTopicWritable(topic);
        if (topic.getStatus() != TopicStatus.OPEN) {
            throw new BusinessRuleException("topic.closed");
        }
        Post parent = null;
        if (form.getReplyToPostId() != null) {
            parent = requirePost(form.getReplyToPostId());
            if (!parent.getTopic().getId().equals(topicId) || parent.getStatus() != PostStatus.VISIBLE) {
                throw new BusinessRuleException("reply.invalidParent");
            }
        }
        Post reply = posts.save(new Post(topic, author, form.getBody().strip(), false, parent));
        topic.registerReply(Instant.now(clock));
        interactions.notifyAboutReply(reply);
        return reply.getId();
    }

    @Transactional(readOnly = true)
    public Forms.PostEdit postEditForm(Long userId, Long postId) {
        User user = authorization.requireUser(userId);
        authorization.requireCanPublish(user);
        Post post = requirePost(postId);
        authorization.requirePostOwner(user, post);
        requirePostEditable(post);
        Forms.PostEdit form = new Forms.PostEdit();
        form.setBody(post.getBody());
        return form;
    }

    @Transactional
    public Long editPost(Long userId, Long postId, Forms.PostEdit form) {
        User user = authorization.requireUser(userId);
        authorization.requireCanPublish(user);
        Long topicId = requirePostTopicId(postId);
        requireTopicForUpdate(topicId);
        Post post = requirePostForUpdate(postId);
        authorization.requirePostOwner(user, post);
        requirePostEditable(post);
        post.edit(form.getBody().strip());
        post.getTopic().touch(Instant.now(clock));
        return topicId;
    }

    @Transactional(readOnly = true)
    public Long ownedPostTopicId(Long userId, Long postId) {
        User user = authorization.requireUser(userId);
        authorization.requireCanPublish(user);
        Post post = requirePost(postId);
        authorization.requirePostOwner(user, post);
        requirePostEditable(post);
        return post.getTopic().getId();
    }

    @Transactional(readOnly = true)
    public PostView postForDeletion(Long userId, Long postId) {
        User user = authorization.requireUser(userId);
        authorization.requireCanPublish(user);
        Post post = requirePost(postId);
        authorization.requirePostOwner(user, post);
        requirePostEditable(post);
        return toPostView(post, user);
    }

    @Transactional(readOnly = true)
    public PostView postForReport(Long userId, Long postId) {
        User user = authorization.requireUser(userId);
        Post post = requirePost(postId);
        if (post.getStatus() != PostStatus.VISIBLE) {
            throw new BusinessRuleException("post.notVisible");
        }
        return toPostView(post, user);
    }

    @Transactional(readOnly = true)
    public Long postTopicId(Long postId) {
        return requirePostTopicId(postId);
    }

    @Transactional(readOnly = true)
    public PostLocation postLocation(Long postId) {
        Post post = requirePost(postId);
        Long topicId = post.getTopic().getId();
        long position = posts.countBefore(topicId, post.getCreatedAt(), postId);
        return new PostLocation(topicId, postId, Math.toIntExact(position / POSTS_PER_PAGE));
    }

    @Transactional(readOnly = true)
    public TopicSummary topicSummary(Long topicId) {
        return views.topic(requireTopic(topicId));
    }

    @Transactional
    public Long deletePost(Long userId, Long postId) {
        User user = authorization.requireUser(userId);
        authorization.requireCanPublish(user);
        Long topicId = requirePostTopicId(postId);
        requireTopicForUpdate(topicId);
        Post post = requirePostForUpdate(postId);
        authorization.requirePostOwner(user, post);
        requirePostEditable(post);
        post.softDelete(user, "Deleted by author");
        if (post.isInitialPost()) {
            post.getTopic().setStatus(TopicStatus.ARCHIVED, user);
        }
        return topicId;
    }

    @Transactional(readOnly = true)
    public Long initialPostId(Long topicId) {
        requireTopic(topicId);
        return initialPost(topicId).getId();
    }

    @Transactional(readOnly = true)
    public Page<TopicSummary> search(String query, Long categoryId, int page) {
        String normalized = query == null ? "" : query.strip();
        if (normalized.length() < 2) {
            return Page.empty(PageRequest.of(safePage(page), 20));
        }
        return topics.search(normalized, categoryId, TopicStatus.ARCHIVED, PostStatus.VISIBLE,
                        PageRequest.of(safePage(page), 20))
                .map(views::topic);
    }

    @Transactional(readOnly = true)
    public UserProfile profile(String username, Long viewerId, int topicPage, int postPage) {
        User user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User viewer = viewerId == null ? null : authorization.requireUser(viewerId);
        Page<TopicSummary> userTopics = topics
                .findByAuthorIdAndStatusNotAndCategoryEnabledTrueOrderByCreatedAtDesc(
                        user.getId(), TopicStatus.ARCHIVED, PageRequest.of(safePage(topicPage), 10))
                .map(views::topic);
        Page<PostView> userPosts = posts
                .findByAuthorIdAndInitialPostFalseAndTopicStatusNotAndTopicCategoryEnabledTrueOrderByCreatedAtDesc(
                        user.getId(), TopicStatus.ARCHIVED, PageRequest.of(safePage(postPage), 10))
                .map(post -> toPostView(post, viewer));
        return new UserProfile(
                user.getId(), user.getUsername(), user.getCreatedAt(), user.isBlockedAt(Instant.now(clock)),
                user.getBlockReason(), user.getRoles(), userTopics, userPosts);
    }

    @Transactional(readOnly = true)
    public Topic requireTopic(Long id) {
        return topics.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found"));
    }

    @Transactional(readOnly = true)
    public Post requirePost(Long id) {
        return posts.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    }

    private Category requireCategory(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private Post initialPost(Long topicId) {
        return posts.findByTopicIdAndInitialPostTrue(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Initial post not found"));
    }

    private Topic requireTopicForUpdate(Long id) {
        return topics.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found"));
    }

    private Post requirePostForUpdate(Long id) {
        return posts.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    }

    private Long requirePostTopicId(Long id) {
        return posts.findTopicIdById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    }

    private PostView toPostView(Post post, User viewer) {
        boolean owner = viewer != null && authorization.owns(viewer, post);
        boolean blocked = viewer != null && viewer.isBlockedAt(Instant.now(clock));
        boolean editable = owner && !blocked && post.getStatus() == PostStatus.VISIBLE
                && post.getTopic().getCategory().isEnabled()
                && post.getTopic().getStatus() != TopicStatus.ARCHIVED;
        return views.post(
                post,
                viewer != null && likes.existsByPostIdAndUserId(post.getId(), viewer.getId()),
                editable,
                editable);
    }

    private void applyTags(Topic topic, String rawTags) {
        topic.getTags().stream().toList().forEach(topic::removeTag);
        if (rawTags == null || rawTags.isBlank()) {
            return;
        }
        rawTags.lines()
                .flatMap(line -> List.of(line.split(",")).stream())
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .limit(5)
                .filter(value -> value.length() <= 40)
                .map(value -> tags.findByNameIgnoreCase(value).orElseGet(() -> tags.save(new Tag(value))))
                .forEach(topic::addTag);
    }

    private void requirePostEditable(Post post) {
        authorization.requireTopicWritable(post.getTopic());
        if (post.getStatus() != PostStatus.VISIBLE) {
            throw new BusinessRuleException("post.notEditable");
        }
    }

    private int safePage(int page) {
        return Math.min(MAX_PAGE, Math.max(0, page));
    }
}
