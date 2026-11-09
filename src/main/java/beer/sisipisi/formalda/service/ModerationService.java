package beer.sisipisi.formalda.service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import beer.sisipisi.formalda.dto.Forms;
import beer.sisipisi.formalda.dto.ViewModels.AdminCategoryView;
import beer.sisipisi.formalda.dto.ViewModels.AdminUserView;
import beer.sisipisi.formalda.dto.ViewModels.CategoryOption;
import beer.sisipisi.formalda.dto.ViewModels.ReportView;
import beer.sisipisi.formalda.entity.AuditAction;
import beer.sisipisi.formalda.entity.AuditLog;
import beer.sisipisi.formalda.entity.Category;
import beer.sisipisi.formalda.entity.ModeratorCategory;
import beer.sisipisi.formalda.entity.Post;
import beer.sisipisi.formalda.entity.PostStatus;
import beer.sisipisi.formalda.entity.Report;
import beer.sisipisi.formalda.entity.ReportStatus;
import beer.sisipisi.formalda.entity.Role;
import beer.sisipisi.formalda.entity.Topic;
import beer.sisipisi.formalda.entity.TopicStatus;
import beer.sisipisi.formalda.entity.User;
import beer.sisipisi.formalda.exception.BusinessRuleException;
import beer.sisipisi.formalda.exception.ResourceNotFoundException;
import beer.sisipisi.formalda.repository.AuditLogRepository;
import beer.sisipisi.formalda.repository.CategoryRepository;
import beer.sisipisi.formalda.repository.ModeratorCategoryRepository;
import beer.sisipisi.formalda.repository.PostRepository;
import beer.sisipisi.formalda.repository.ReportRepository;
import beer.sisipisi.formalda.repository.TopicRepository;
import beer.sisipisi.formalda.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationService {
    private static final int MAX_PAGE = 10_000;

    private final AuthorizationService authorization;
    private final ViewMapper views;
    private final TopicRepository topics;
    private final PostRepository posts;
    private final ReportRepository reports;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final ModeratorCategoryRepository assignments;
    private final AuditLogRepository auditLogs;
    private final Clock clock;

    public ModerationService(
            AuthorizationService authorization,
            ViewMapper views,
            TopicRepository topics,
            PostRepository posts,
            ReportRepository reports,
            CategoryRepository categories,
            UserRepository users,
            ModeratorCategoryRepository assignments,
            AuditLogRepository auditLogs,
            Clock clock) {
        this.authorization = authorization;
        this.views = views;
        this.topics = topics;
        this.posts = posts;
        this.reports = reports;
        this.categories = categories;
        this.users = users;
        this.assignments = assignments;
        this.auditLogs = auditLogs;
        this.clock = clock;
    }

    @Transactional
    public void closeTopic(Long actorId, Long topicId) {
        changeTopicStatus(actorId, topicId, TopicStatus.CLOSED);
    }

    @Transactional
    public void reopenTopic(Long actorId, Long topicId) {
        changeTopicStatus(actorId, topicId, TopicStatus.OPEN);
    }

    @Transactional
    public void archiveTopic(Long actorId, Long topicId) {
        changeTopicStatus(actorId, topicId, TopicStatus.ARCHIVED);
    }

    private void changeTopicStatus(Long actorId, Long topicId, TopicStatus target) {
        User actor = authorization.requireUser(actorId);
        Topic topic = requireTopicForUpdate(topicId);
        authorization.requireTopicWritable(topic);
        boolean moderator = authorization.canModerate(actor, topic.getCategory());
        if (target == TopicStatus.CLOSED) {
            if (!authorization.owns(actor, topic) && !moderator) {
                throw new AccessDeniedException("Cannot close this topic");
            }
            if (!moderator) {
                authorization.requireCanPublish(actor);
            }
        } else if (!moderator) {
            throw new AccessDeniedException("Moderator permission required");
        }
        TopicStatus previous = topic.getStatus();
        if (previous == target) {
            return;
        }
        topic.setStatus(target, actor);
        AuditAction action = switch (target) {
            case OPEN -> AuditAction.TOPIC_REOPENED;
            case CLOSED -> AuditAction.TOPIC_CLOSED;
            case ARCHIVED -> AuditAction.TOPIC_ARCHIVED;
        };
        audit(actor, action, "Topic", topicId, null, previous + " -> " + target);
    }

    @Transactional
    public boolean togglePinned(Long actorId, Long topicId) {
        User actor = authorization.requireUser(actorId);
        Topic topic = requireTopicForUpdate(topicId);
        authorization.requireTopicWritable(topic);
        authorization.requireModerator(actor, topic.getCategory());
        topic.setPinned(!topic.isPinned());
        audit(actor,
                topic.isPinned() ? AuditAction.TOPIC_PINNED : AuditAction.TOPIC_UNPINNED,
                "Topic", topicId, null, null);
        return topic.isPinned();
    }

    @Transactional(readOnly = true)
    public Page<ReportView> reportQueue(Long actorId, int page) {
        User actor = authorization.requireUser(actorId);
        Page<Report> result;
        if (actor.hasRole(Role.ADMIN)) {
            result = reports.findByStatusOrderByCreatedAtAsc(
                    ReportStatus.PENDING, PageRequest.of(safePage(page), 20));
        } else if (actor.hasRole(Role.MODERATOR)) {
            result = reports.findModerationQueue(
                    actorId, ReportStatus.PENDING, PageRequest.of(safePage(page), 20));
        } else {
            throw new AccessDeniedException("Moderator permission required");
        }
        return result.map(views::report);
    }

    @Transactional(readOnly = true)
    public ReportView report(Long actorId, Long reportId) {
        User actor = authorization.requireUser(actorId);
        Report report = requireReport(reportId);
        authorization.requireModerator(actor, report.getPost().getTopic().getCategory());
        return views.report(report);
    }

    @Transactional
    public void resolveReport(Long actorId, Long reportId, Forms.ModerationDecision form) {
        User actor = authorization.requireUser(actorId);
        Long postId = reports.findPostIdById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        Long topicId = posts.findTopicIdById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        Topic topic = requireTopicForUpdate(topicId);
        Post post = requirePostForUpdate(postId);
        Report report = requireReportForUpdate(reportId);
        authorization.requireModerator(actor, topic.getCategory());
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessRuleException("report.alreadyResolved");
        }
        String reason = form.getReason().strip();
        switch (form.getAction().toUpperCase(Locale.ROOT)) {
            case "REJECT" -> {
                report.resolve(ReportStatus.REJECTED, actor, reason);
                audit(actor, AuditAction.REPORT_REJECTED, "Report", reportId, reason, null);
            }
            case "HIDE" -> {
                hideReportedPost(actor, post, reason);
                report.resolve(ReportStatus.ACTION_TAKEN, actor, reason);
                audit(actor, AuditAction.REPORT_RESOLVED, "Report", reportId, reason, "HIDE");
            }
            case "BLOCK" -> {
                if (form.getBlockDays() == null) {
                    throw new BusinessRuleException("report.blockDaysRequired");
                }
                User target = findUserForUpdate(post.getAuthor().getId());
                hideReportedPost(actor, post, reason);
                Instant blockedUntil = Instant.now(clock).plusSeconds(form.getBlockDays() * 86_400L);
                if (!target.isBlockedPermanently()
                        && (target.getBlockedUntil() == null || target.getBlockedUntil().isBefore(blockedUntil))) {
                    target.blockUntil(blockedUntil, reason);
                }
                report.resolve(ReportStatus.ACTION_TAKEN, actor, reason);
                audit(actor, AuditAction.USER_BLOCKED, "User", target.getId(), reason,
                        "days=" + form.getBlockDays());
                audit(actor, AuditAction.REPORT_RESOLVED, "Report", reportId, reason, "BLOCK");
            }
            default -> throw new BusinessRuleException("report.invalidAction");
        }
    }

    @Transactional(readOnly = true)
    public Page<AdminCategoryView> allCategories(Long actorId, int page) {
        requireAdmin(actorId);
        return categories.findAllByOrderBySortOrderAscNameAsc(PageRequest.of(safePage(page), 20))
                .map(views::adminCategory);
    }

    @Transactional(readOnly = true)
    public List<CategoryOption> allCategoryOptionsForAdministration(Long actorId) {
        requireAdmin(actorId);
        return categories.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(views::categoryOption)
                .toList();
    }

    @Transactional(readOnly = true)
    public Forms.Category categoryForm(Long actorId, Long id) {
        requireAdmin(actorId);
        Category category = findCategory(id);
        Forms.Category form = new Forms.Category();
        form.setName(category.getName());
        form.setDescription(category.getDescription());
        form.setSortOrder(category.getSortOrder());
        form.setEnabled(category.isEnabled());
        return form;
    }

    @Transactional
    public void createCategory(Long actorId, Forms.Category form) {
        User actor = requireAdmin(actorId);
        String slug = uniqueSlug(form.getName(), null);
        Category category = categories.save(new Category(
                slug, form.getName().strip(), form.getDescription().strip(), form.getSortOrder()));
        category.setEnabled(form.isEnabled());
        audit(actor, AuditAction.CATEGORY_CREATED, "Category", category.getId(), null, null);
    }

    @Transactional
    public void updateCategory(Long actorId, Long categoryId, Forms.Category form) {
        User actor = requireAdmin(actorId);
        Category category = findCategory(categoryId);
        String slug = category.getName().equals(form.getName().strip())
                ? category.getSlug() : uniqueSlug(form.getName(), category.getId());
        boolean wasEnabled = category.isEnabled();
        category.update(slug, form.getName().strip(), form.getDescription().strip(), form.getSortOrder());
        category.setEnabled(form.isEnabled());
        audit(actor, AuditAction.CATEGORY_UPDATED, "Category", categoryId, null, null);
        if (wasEnabled != form.isEnabled()) {
            audit(actor,
                    form.isEnabled() ? AuditAction.CATEGORY_ENABLED : AuditAction.CATEGORY_DISABLED,
                    "Category", categoryId, null, null);
        }
    }

    @Transactional(readOnly = true)
    public Page<AdminUserView> users(Long actorId, String query, int page) {
        requireAdmin(actorId);
        PageRequest request = PageRequest.of(safePage(page), 20);
        Page<User> result = query == null || query.isBlank()
                ? users.findAll(request)
                : users.search(query.strip(), request);
        return result.map(views::adminUser);
    }

    @Transactional(readOnly = true)
    public AdminUserView user(Long actorId, Long id) {
        requireAdmin(actorId);
        return views.adminUser(findUser(id));
    }

    @Transactional(readOnly = true)
    public Forms.UserAdministration userAdministrationForm(Long actorId, Long userId) {
        requireAdmin(actorId);
        User user = findUser(userId);
        Instant now = Instant.now(clock);
        Forms.UserAdministration form = new Forms.UserAdministration();
        form.setRoles(user.getRoles().stream().map(Role::name)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
        form.setModeratedCategoryIds(findModeratedCategoryIds(userId));
        form.setBlocked(user.isBlockedAt(now));
        if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(now)) {
            form.setBlockedUntil(user.getBlockedUntil().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        form.setReason(user.getBlockReason());
        return form;
    }

    @Transactional
    public void updateUser(Long actorId, Long targetId, Forms.UserAdministration form) {
        List<User> lockedAdministrators = users.findAllByRoleForUpdate(Role.ADMIN);
        User actor = requireAdmin(actorId);
        User target = findUserForUpdate(targetId);
        Set<Role> previousRoles = EnumSet.noneOf(Role.class);
        previousRoles.addAll(target.getRoles());
        Set<Role> requested = EnumSet.of(Role.USER);
        Set<String> submittedRoles = form.getRoles() == null ? Set.of() : form.getRoles();
        try {
            for (String role : submittedRoles) {
                requested.add(Role.valueOf(role));
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessRuleException("admin.invalidRole");
        }
        if (actorId.equals(targetId) && !requested.equals(target.getRoles())) {
            throw new BusinessRuleException("admin.cannotChangeOwnRoles");
        }
        if (target.hasRole(Role.ADMIN) && !requested.contains(Role.ADMIN)) {
            boolean targetIsStillAdministrator = lockedAdministrators.stream()
                    .anyMatch(administrator -> administrator.getId().equals(targetId));
            if (targetIsStillAdministrator && lockedAdministrators.size() <= 1) {
                throw new BusinessRuleException("admin.lastAdministrator");
            }
        }
        for (Role role : Role.values()) {
            if (requested.contains(role)) {
                target.addRole(role);
            } else {
                target.removeRole(role);
            }
        }
        updateAssignments(actor, target, requested, form.getModeratedCategoryIds());
        String reason = form.getReason() == null ? null : form.getReason().strip();
        Instant now = Instant.now(clock);
        if (form.isBlocked()) {
            if (reason == null || reason.isBlank()) {
                throw new BusinessRuleException("admin.blockReasonRequired");
            }
            if (form.getBlockedUntil() == null) {
                target.blockPermanently(reason);
            } else {
                Instant blockedUntil = form.getBlockedUntil().toInstant(ZoneOffset.UTC);
                if (!blockedUntil.isAfter(now)) {
                    throw new BusinessRuleException("admin.blockUntilFuture");
                }
                target.blockUntil(blockedUntil, reason);
            }
            audit(actor, AuditAction.USER_BLOCKED, "User", targetId, reason, null);
        } else if (target.isBlockedPermanently() || target.getBlockedUntil() != null) {
            target.unblock();
            audit(actor, AuditAction.USER_UNBLOCKED, "User", targetId, reason, null);
        }
        if (!previousRoles.equals(requested)) {
            audit(actor, AuditAction.ROLE_CHANGED, "User", targetId, reason, requested.toString());
        }
    }

    private void updateAssignments(User actor, User target, Set<Role> roles, Set<Long> requestedIds) {
        Set<Long> requested = roles.contains(Role.MODERATOR) && requestedIds != null
                ? requestedIds : Set.of();
        var existing = assignments.findByModeratorIdOrderByCategorySortOrderAsc(target.getId());
        for (ModeratorCategory assignment : existing) {
            if (!requested.contains(assignment.getCategory().getId())) {
                assignments.delete(assignment);
                audit(actor, AuditAction.MODERATOR_REMOVED, "Category",
                        assignment.getCategory().getId(), null, "user=" + target.getId());
            }
        }
        Set<Long> existingIds = existing.stream().map(value -> value.getCategory().getId())
                .collect(java.util.stream.Collectors.toSet());
        for (Long categoryId : requested) {
            if (!existingIds.contains(categoryId)) {
                Category category = findCategory(categoryId);
                assignments.save(new ModeratorCategory(target, category, actor));
                audit(actor, AuditAction.MODERATOR_ASSIGNED, "Category", categoryId,
                        null, "user=" + target.getId());
            }
        }
    }

    private User requireAdmin(Long id) {
        User user = authorization.requireUser(id);
        if (!user.hasRole(Role.ADMIN)) {
            throw new AccessDeniedException("Administrator permission required");
        }
        return user;
    }

    private User findUser(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private User findUserForUpdate(Long id) {
        return users.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Category findCategory(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private Set<Long> findModeratedCategoryIds(Long userId) {
        return assignments.findByModeratorIdOrderByCategorySortOrderAsc(userId).stream()
                .map(value -> value.getCategory().getId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Topic requireTopic(Long id) {
        return topics.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found"));
    }

    private Topic requireTopicForUpdate(Long id) {
        return topics.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found"));
    }

    private Post requirePostForUpdate(Long id) {
        return posts.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    }

    private Report requireReport(Long id) {
        return reports.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
    }

    private Report requireReportForUpdate(Long id) {
        return reports.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
    }

    private String uniqueSlug(String name, Long categoryId) {
        String base = Normalizer.normalize(name.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFKC)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "category";
        }
        String candidate = base;
        int suffix = 2;
        while (categories.findBySlug(candidate)
                .filter(category -> !category.getId().equals(categoryId)).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private void audit(User actor, AuditAction action, String targetType, Long targetId,
                       String reason, String details) {
        auditLogs.save(new AuditLog(actor, action, targetType, targetId, reason, details));
    }

    private void hideReportedPost(User actor, Post post, String reason) {
        if (post.getStatus() == PostStatus.VISIBLE) {
            post.hide(actor, reason);
            audit(actor, AuditAction.POST_HIDDEN, "Post", post.getId(), reason, null);
        }
    }

    private int safePage(int page) {
        return Math.min(MAX_PAGE, Math.max(0, page));
    }
}
