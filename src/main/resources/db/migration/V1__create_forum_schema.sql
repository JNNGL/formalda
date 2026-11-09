CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    blocked_permanently BOOLEAN NOT NULL DEFAULT FALSE,
    blocked_until TIMESTAMP(6) NULL,
    block_reason VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT chk_users_block CHECK (NOT blocked_permanently OR blocked_until IS NULL)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_user_roles_role CHECK (role IN ('USER', 'MODERATOR', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE email_verification_codes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    resend_available_at TIMESTAMP(6) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_verification_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_verification_attempts CHECK (attempt_count BETWEEN 0 AND 5),
    INDEX idx_verification_user_active (user_id, consumed_at, created_at),
    INDEX idx_verification_expiry (expires_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slug VARCHAR(80) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_slug UNIQUE (slug),
    INDEX idx_categories_public_order (enabled, sort_order, name)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE moderator_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    moderator_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    assigned_by_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_moderator_categories_moderator_category UNIQUE (moderator_id, category_id),
    CONSTRAINT fk_moderator_categories_moderator FOREIGN KEY (moderator_id) REFERENCES users (id),
    CONSTRAINT fk_moderator_categories_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE,
    CONSTRAINT fk_moderator_categories_assigner FOREIGN KEY (assigned_by_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_moderator_categories_category (category_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE topics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    title VARCHAR(180) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    last_activity_at TIMESTAMP(6) NOT NULL,
    reply_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    status_changed_by_id BIGINT NULL,
    status_changed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_topics_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_topics_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT fk_topics_status_actor FOREIGN KEY (status_changed_by_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_topics_status CHECK (status IN ('OPEN', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT chk_topics_counts CHECK (reply_count >= 0 AND like_count >= 0 AND view_count >= 0),
    INDEX idx_topics_category_activity (category_id, status, pinned, last_activity_at),
    INDEX idx_topics_category_created (category_id, status, pinned, created_at),
    INDEX idx_topics_category_popularity (category_id, status, pinned, reply_count, like_count, last_activity_at),
    INDEX idx_topics_author_created (author_id, created_at),
    FULLTEXT INDEX ft_topics_title (title)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    parent_post_id BIGINT NULL,
    body TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'VISIBLE',
    initial_post BOOLEAN NOT NULL DEFAULT FALSE,
    initial_topic_id BIGINT GENERATED ALWAYS AS (CASE WHEN initial_post THEN topic_id ELSE NULL END) VIRTUAL,
    edited_at TIMESTAMP(6) NULL,
    removed_at TIMESTAMP(6) NULL,
    removal_reason VARCHAR(1000) NULL,
    removed_by_id BIGINT NULL,
    like_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_posts_one_initial_per_topic UNIQUE (initial_topic_id),
    CONSTRAINT fk_posts_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    CONSTRAINT fk_posts_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT fk_posts_parent FOREIGN KEY (parent_post_id) REFERENCES posts (id) ON DELETE SET NULL,
    CONSTRAINT fk_posts_removed_by FOREIGN KEY (removed_by_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_posts_status CHECK (status IN ('VISIBLE', 'DELETED', 'HIDDEN')),
    CONSTRAINT chk_posts_like_count CHECK (like_count >= 0),
    INDEX idx_posts_topic_chronology (topic_id, created_at, id),
    INDEX idx_posts_author_created (author_id, created_at),
    INDEX idx_posts_parent (parent_post_id),
    FULLTEXT INDEX ft_posts_body (body)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(40) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tags_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE topic_tags (
    topic_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (topic_id, tag_id),
    CONSTRAINT fk_topic_tags_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    CONSTRAINT fk_topic_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE,
    INDEX idx_topic_tags_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE post_likes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_post_likes_post_user UNIQUE (post_id, user_id),
    CONSTRAINT fk_post_likes_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_likes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_post_likes_user (user_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE bookmarks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_bookmarks_user_topic UNIQUE (user_id, topic_id),
    CONSTRAINT fk_bookmarks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_bookmarks_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    INDEX idx_bookmarks_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_subscriptions_user_topic UNIQUE (user_id, topic_id),
    CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_subscriptions_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    INDEX idx_subscriptions_user_created (user_id, created_at),
    INDEX idx_subscriptions_topic (topic_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    actor_id BIGINT NULL,
    topic_id BIGINT NULL,
    post_id BIGINT NULL,
    type VARCHAR(32) NOT NULL,
    read_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT chk_notifications_type CHECK (type IN ('NEW_TOPIC_REPLY', 'DIRECT_REPLY')),
    CONSTRAINT chk_notifications_target CHECK (topic_id IS NOT NULL OR post_id IS NOT NULL),
    INDEX idx_notifications_recipient_created (recipient_id, created_at),
    INDEX idx_notifications_recipient_unread (recipient_id, read_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    resolved_by_id BIGINT NULL,
    resolution_reason VARCHAR(1000) NULL,
    resolved_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_reports_post FOREIGN KEY (post_id) REFERENCES posts (id),
    CONSTRAINT fk_reports_resolver FOREIGN KEY (resolved_by_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_reports_status CHECK (status IN ('PENDING', 'REJECTED', 'ACTION_TAKEN')),
    INDEX idx_reports_status_created (status, created_at),
    INDEX idx_reports_post (post_id),
    INDEX idx_reports_reporter_created (reporter_id, created_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id BIGINT NULL,
    reason VARCHAR(1000) NULL,
    details TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_audit_log_action CHECK (action IN (
        'CATEGORY_CREATED', 'CATEGORY_UPDATED', 'CATEGORY_DISABLED', 'CATEGORY_ENABLED',
        'TOPIC_PINNED', 'TOPIC_UNPINNED', 'TOPIC_CLOSED', 'TOPIC_REOPENED', 'TOPIC_ARCHIVED',
        'POST_HIDDEN', 'REPORT_REJECTED', 'REPORT_RESOLVED', 'USER_BLOCKED', 'USER_UNBLOCKED',
        'ROLE_CHANGED', 'MODERATOR_ASSIGNED', 'MODERATOR_REMOVED'
    )),
    INDEX idx_audit_log_created (created_at),
    INDEX idx_audit_log_actor_created (actor_id, created_at),
    INDEX idx_audit_log_target_created (target_type, target_id, created_at),
    INDEX idx_audit_log_action_created (action, created_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
