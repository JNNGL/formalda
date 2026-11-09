package beer.sisipisi.formalda.service;

import java.time.Clock;
import java.time.Instant;

import beer.sisipisi.formalda.entity.Category;
import beer.sisipisi.formalda.entity.Post;
import beer.sisipisi.formalda.entity.Role;
import beer.sisipisi.formalda.entity.Topic;
import beer.sisipisi.formalda.entity.TopicStatus;
import beer.sisipisi.formalda.entity.User;
import beer.sisipisi.formalda.exception.BusinessRuleException;
import beer.sisipisi.formalda.exception.ResourceNotFoundException;
import beer.sisipisi.formalda.repository.ModeratorCategoryRepository;
import beer.sisipisi.formalda.repository.UserRepository;
import beer.sisipisi.formalda.security.FormaldaPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationService {
    private final UserRepository users;
    private final ModeratorCategoryRepository moderatorCategories;
    private final Clock clock;

    public AuthorizationService(
            UserRepository users,
            ModeratorCategoryRepository moderatorCategories,
            Clock clock) {
        this.users = users;
        this.moderatorCategories = moderatorCategories;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public User requireUser(FormaldaPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return requireUser(principal.id());
    }

    @Transactional(readOnly = true)
    public User requireUser(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public void requireCanPublish(User user) {
        if (!user.isEnabled() || !user.isEmailVerified()) {
            throw new AccessDeniedException("Account is not active");
        }
        if (user.isBlockedAt(Instant.now(clock))) {
            throw new BusinessRuleException("account.blocked");
        }
    }

    public void requireTopicWritable(Topic topic) {
        if (!topic.getCategory().isEnabled()) {
            throw new BusinessRuleException("category.disabled");
        }
        if (topic.getStatus() == TopicStatus.ARCHIVED) {
            throw new BusinessRuleException("topic.archived");
        }
    }

    @Transactional(readOnly = true)
    public boolean canModerate(User user, Category category) {
        return user.hasRole(Role.ADMIN)
                || (user.hasRole(Role.MODERATOR)
                && moderatorCategories.existsByModeratorIdAndCategoryId(user.getId(), category.getId()));
    }

    public void requireModerator(User user, Category category) {
        if (!canModerate(user, category)) {
            throw new AccessDeniedException("Moderator permission required");
        }
    }

    public boolean owns(User user, Post post) {
        return post.getAuthor().getId().equals(user.getId());
    }

    public boolean owns(User user, Topic topic) {
        return topic.getAuthor().getId().equals(user.getId());
    }

    public void requirePostOwner(User user, Post post) {
        if (!owns(user, post)) {
            throw new AccessDeniedException("Only the author can change this post");
        }
    }
}
