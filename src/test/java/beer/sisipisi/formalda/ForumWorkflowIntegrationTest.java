package beer.sisipisi.formalda;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import beer.sisipisi.formalda.dto.Forms;
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
import beer.sisipisi.formalda.repository.BookmarkRepository;
import beer.sisipisi.formalda.repository.CategoryRepository;
import beer.sisipisi.formalda.repository.ModeratorCategoryRepository;
import beer.sisipisi.formalda.repository.PostLikeRepository;
import beer.sisipisi.formalda.repository.PostRepository;
import beer.sisipisi.formalda.repository.ReportRepository;
import beer.sisipisi.formalda.repository.SubscriptionRepository;
import beer.sisipisi.formalda.repository.TopicRepository;
import beer.sisipisi.formalda.repository.UserRepository;
import beer.sisipisi.formalda.security.FormaldaPrincipal;
import beer.sisipisi.formalda.service.ForumService;
import beer.sisipisi.formalda.service.InteractionService;
import beer.sisipisi.formalda.service.ModerationService;
import beer.sisipisi.formalda.support.MariaDbIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class ForumWorkflowIntegrationTest extends MariaDbIntegrationTest {
    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    UserRepository users;
    @Autowired
    CategoryRepository categories;
    @Autowired
    TopicRepository topics;
    @Autowired
    PostRepository posts;
    @Autowired
    PostLikeRepository likes;
    @Autowired
    BookmarkRepository bookmarks;
    @Autowired
    SubscriptionRepository subscriptions;
    @Autowired
    ReportRepository reports;
    @Autowired
    ModeratorCategoryRepository moderatorCategories;
    @Autowired
    ForumService forum;
    @Autowired
    InteractionService interactions;
    @Autowired
    ModerationService moderation;
    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbc.batchUpdate(
                "DELETE FROM audit_log",
                "DELETE FROM reports",
                "DELETE FROM notifications",
                "DELETE FROM subscriptions",
                "DELETE FROM bookmarks",
                "DELETE FROM post_likes",
                "DELETE FROM topic_tags",
                "DELETE FROM tags",
                "DELETE FROM posts",
                "DELETE FROM topics",
                "DELETE FROM moderator_categories",
                "DELETE FROM categories",
                "DELETE FROM email_verification_codes",
                "DELETE FROM user_roles",
                "DELETE FROM users");
    }

    @Test
    void verifiedAccountCanLogInByEmailAndUsernameWhileUnverifiedAccountCannot() throws Exception {
        createUser("pending@example.test", "pending", false);
        createUser("verified@example.test", "verified", true);

        mvc.perform(formLogin("/auth/login")
                        .userParameter("identifier")
                        .user("verified@example.test")
                        .password(PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("verified"));

        mvc.perform(formLogin("/auth/login")
                        .userParameter("identifier")
                        .user("verified")
                        .password(PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("verified"));

        mvc.perform(formLogin("/auth/login")
                        .userParameter("identifier")
                        .user("pending@example.test")
                        .password(PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void renamedUsernameIsStoredInTheCurrentSecurityContext() throws Exception {
        User member = createUser("rename@example.test", "before-rename", true);

        var result = mvc.perform(post("/me/profile")
                        .with(user(FormaldaPrincipal.from(member)))
                        .with(csrf())
                        .param("username", "after-rename"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/after-rename"))
                .andExpect(authenticated().withUsername("after-rename"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/me/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername("after-rename"))
                .andExpect(model().attribute("form", org.hamcrest.Matchers.hasProperty(
                        "username", org.hamcrest.Matchers.is("after-rename"))));
    }

    @Test
    void topicAndReplyFormsRequireCsrfAndUsePostRedirectGet() throws Exception {
        User author = createUser("author@example.test", "author", true);
        Category category = createCategory();

        mvc.perform(post("/topics/new")
                        .with(user(FormaldaPrincipal.from(author)))
                        .param("categoryId", category.getId().toString())
                        .param("title", "A topic without CSRF")
                        .param("body", "This request must be rejected."))
                .andExpect(status().isForbidden());
        assertThat(topics.count()).isZero();

        mvc.perform(post("/topics/new")
                        .with(user(FormaldaPrincipal.from(author)))
                        .with(csrf())
                        .param("categoryId", category.getId().toString())
                        .param("title", "Integration test topic")
                        .param("body", "The initial post body")
                        .param("tags", "testing, mvc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/topics/*"));

        Topic topic = topics.findAll().getFirst();
        Post initialPost = posts.findByTopicIdAndInitialPostTrue(topic.getId()).orElseThrow();
        assertThat(initialPost.getBody()).isEqualTo("The initial post body");

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"));

        mvc.perform(post("/topics/{id}/replies", topic.getId())
                        .with(user(FormaldaPrincipal.from(author)))
                        .with(csrf())
                        .param("body", "A reply created through MockMvc")
                        .param("replyToPostId", initialPost.getId().toString()))
                .andExpect(status().is3xxRedirection());

        assertThat(posts.findByTopicIdOrderByCreatedAtAscIdAsc(
                        topic.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent())
                .extracting(Post::getBody)
                .containsExactly("The initial post body", "A reply created through MockMvc");
        assertThat(topics.findById(topic.getId()).orElseThrow().getReplyCount()).isOne();
    }

    @Test
    void anotherUserCannotEditTopicOrPost() throws Exception {
        User owner = createUser("owner@example.test", "owner", true);
        User intruder = createUser("intruder@example.test", "intruder", true);
        TopicFixture fixture = createTopic(owner, createCategory());

        mvc.perform(post("/topics/{id}/edit", fixture.topic().getId())
                        .with(user(FormaldaPrincipal.from(intruder)))
                        .with(csrf())
                        .param("categoryId", fixture.topic().getCategory().getId().toString())
                        .param("title", "Stolen topic title")
                        .param("body", "Stolen topic body"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));

        mvc.perform(post("/posts/{id}/edit", fixture.initialPost().getId())
                        .with(user(FormaldaPrincipal.from(intruder)))
                        .with(csrf())
                        .param("body", "Stolen post body"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));

        assertThat(topics.findById(fixture.topic().getId()).orElseThrow().getTitle())
                .isEqualTo("Fixture topic");
        assertThat(posts.findById(fixture.initialPost().getId()).orElseThrow().getBody())
                .isEqualTo("Fixture initial body");
    }

    @Test
    void closedTopicRemainsReadableButRejectsNewReplies() throws Exception {
        User owner = createUser("closer@example.test", "closer", true);
        User moderator = createUser("reopener@example.test", "reopener", true, Role.MODERATOR);
        Category category = createCategory();
        TopicFixture fixture = createTopic(owner, category);
        moderatorCategories.saveAndFlush(new ModeratorCategory(moderator, category, moderator));

        mvc.perform(post("/topics/{id}/close", fixture.topic().getId())
                        .with(user(FormaldaPrincipal.from(owner)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics/" + fixture.topic().getId()));
        assertThat(topics.findById(fixture.topic().getId()).orElseThrow().getStatus())
                .isEqualTo(TopicStatus.CLOSED);

        mvc.perform(get("/topics/{id}", fixture.topic().getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("topics/show"))
                .andExpect(model().attribute("canReply", false));

        mvc.perform(post("/topics/{id}/replies", fixture.topic().getId())
                        .with(user(FormaldaPrincipal.from(owner)))
                        .with(csrf())
                        .param("body", "This reply must not be persisted"))
                .andExpect(status().isConflict())
                .andExpect(view().name("error/error"));
        assertThat(posts.countByTopicIdAndInitialPostFalseAndStatus(
                fixture.topic().getId(), PostStatus.VISIBLE)).isZero();

        mvc.perform(post("/topics/{id}/reopen", fixture.topic().getId())
                        .with(user(FormaldaPrincipal.from(moderator)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics/" + fixture.topic().getId()));
        assertThat(topics.findById(fixture.topic().getId()).orElseThrow().getStatus())
                .isEqualTo(TopicStatus.OPEN);
    }

    @Test
    void assignedModeratorCanBlockReportedAuthorAndPublishingIsThenRejected() throws Exception {
        User author = createUser("reported@example.test", "reported", true);
        User reporter = createUser("reporter@example.test", "reporter", true);
        User moderator = createUser("moderator@example.test", "moderator", true, Role.MODERATOR);
        Category category = createCategory();
        TopicFixture fixture = createTopic(author, category);
        moderatorCategories.saveAndFlush(new ModeratorCategory(moderator, category, moderator));

        mvc.perform(post("/reports/new")
                        .with(user(FormaldaPrincipal.from(reporter)))
                        .with(csrf())
                        .param("postId", fixture.initialPost().getId().toString())
                        .param("reason", "This content violates the forum rules."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics/" + fixture.topic().getId()
                        + "?page=0#post-" + fixture.initialPost().getId()));

        Report report = reports.findAll().getFirst();
        mvc.perform(get("/moderation/reports")
                        .with(user(FormaldaPrincipal.from(moderator))))
                .andExpect(status().isOk())
                .andExpect(view().name("moderation/queue"));

        mvc.perform(post("/moderation/reports/{id}", report.getId())
                        .with(user(FormaldaPrincipal.from(moderator)))
                        .with(csrf())
                        .param("action", "BLOCK")
                        .param("reason", "Confirmed rule violation")
                        .param("blockDays", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/moderation/reports"));

        Report resolved = reports.findById(report.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(ReportStatus.ACTION_TAKEN);
        assertThat(resolved.getResolvedBy().getId()).isEqualTo(moderator.getId());
        assertThat(users.findById(author.getId()).orElseThrow().getBlockedUntil()).isNotNull();

        mvc.perform(post("/topics/new")
                        .with(user(FormaldaPrincipal.from(author)))
                        .with(csrf())
                        .param("categoryId", category.getId().toString())
                        .param("title", "Blocked author topic")
                        .param("body", "This must not be created"))
                .andExpect(status().isConflict())
                .andExpect(view().name("error/error"));
        mvc.perform(post("/posts/{id}/edit", fixture.initialPost().getId())
                        .with(user(FormaldaPrincipal.from(author)))
                        .with(csrf())
                        .param("body", "This edit must not be persisted"))
                .andExpect(status().isConflict())
                .andExpect(view().name("error/error"));

        assertThat(topics.count()).isOne();
        assertThat(posts.findById(fixture.initialPost().getId()).orElseThrow().getBody())
                .isEqualTo("Fixture initial body");
    }

    @Test
    void repeatedLikeBookmarkAndSubscriptionRequestsToggleWithoutDuplicates() throws Exception {
        User member = createUser("member@example.test", "member", true);
        TopicFixture fixture = createTopic(member, createCategory());
        FormaldaPrincipal principal = FormaldaPrincipal.from(member);

        mvc.perform(post("/posts/{id}/like", fixture.initialPost().getId())
                        .with(user(principal)).with(csrf())
                        .param("topicId", fixture.topic().getId().toString()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/posts/{id}/like", fixture.initialPost().getId())
                        .with(user(principal)).with(csrf())
                        .param("topicId", fixture.topic().getId().toString()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/posts/{id}/like", fixture.initialPost().getId())
                        .with(user(principal)).with(csrf())
                        .param("topicId", fixture.topic().getId().toString()))
                .andExpect(status().is3xxRedirection());

        toggleTopicInteraction("bookmark", fixture.topic().getId(), principal);
        toggleTopicInteraction("bookmark", fixture.topic().getId(), principal);
        toggleTopicInteraction("bookmark", fixture.topic().getId(), principal);
        toggleTopicInteraction("subscribe", fixture.topic().getId(), principal);
        toggleTopicInteraction("subscribe", fixture.topic().getId(), principal);
        toggleTopicInteraction("subscribe", fixture.topic().getId(), principal);

        assertThat(likes.countByPostId(fixture.initialPost().getId())).isOne();
        assertThat(bookmarks.findByUserIdAndTopicId(member.getId(), fixture.topic().getId())).isPresent();
        assertThat(subscriptions.findByUserIdAndTopicId(member.getId(), fixture.topic().getId())).isPresent();
        assertThat(likes.count()).isOne();
        assertThat(bookmarks.count()).isOne();
        assertThat(subscriptions.count()).isOne();
        assertThat(posts.findById(fixture.initialPost().getId()).orElseThrow().getLikeCount()).isOne();
        assertThat(topics.findById(fixture.topic().getId()).orElseThrow().getLikeCount()).isOne();
    }

    @Test
    void replyAndPublicPostPermalinkRedirectToContainingPage() throws Exception {
        User author = createUser("permalink@example.test", "permalink", true);
        TopicFixture fixture = createTopic(author, createCategory());
        for (int index = 1; index <= 24; index++) {
            posts.saveAndFlush(new Post(
                    fixture.topic(), author, "Existing reply " + index, false, null));
        }

        var result = mvc.perform(post("/topics/{id}/replies", fixture.topic().getId())
                        .with(user(FormaldaPrincipal.from(author)))
                        .with(csrf())
                        .param("body", "The twenty-sixth publication"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Post lastPost = posts.findByTopicIdOrderByCreatedAtAscIdAsc(
                        fixture.topic().getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent().getLast();
        String expectedUrl = "/topics/" + fixture.topic().getId()
                + "?page=1#post-" + lastPost.getId();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(expectedUrl);

        jdbc.update("UPDATE posts SET created_at = ? WHERE topic_id = ?",
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), fixture.topic().getId());

        mvc.perform(get("/posts/{id}", lastPost.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(expectedUrl));
        mvc.perform(get("/posts/{id}", lastPost.getId()).param("reply", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics/" + fixture.topic().getId()
                        + "?page=1&replyTo=" + lastPost.getId() + "#post-" + lastPost.getId()));
    }

    @Test
    void hugePageNumbersAreCappedAcrossPublicPersonalAndAdminLists() throws Exception {
        User administrator = createUser(
                "page-admin@example.test", "page-admin", true, Role.ADMIN);
        Category category = createCategory();
        for (int index = 1; index <= 21; index++) {
            topics.saveAndFlush(new Topic(category, administrator, "Page cap topic " + index));
        }
        FormaldaPrincipal principal = FormaldaPrincipal.from(administrator);
        String hugePage = Integer.toString(Integer.MAX_VALUE);

        mvc.perform(get("/categories/{id}", category.getId()).param("page", hugePage))
                .andExpect(status().isOk())
                .andExpect(model().attribute("topics", hasProperty("number", is(10_000))));
        mvc.perform(get("/me/bookmarks").param("page", hugePage).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("bookmarks", hasProperty("number", is(10_000))));
        mvc.perform(get("/admin/users").param("page", hugePage).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("users", hasProperty("number", is(10_000))));
    }

    @Test
    void categoryLastActivityUsesNewestTopicRegardlessOfPin() {
        User author = createUser("activity@example.test", "activity", true);
        Category category = createCategory();
        Instant olderActivity = Instant.parse("2026-01-01T00:00:00Z");
        Instant newerActivity = Instant.parse("2026-01-02T00:00:00Z");

        Topic pinnedOlderTopic = new Topic(category, author, "Pinned older topic");
        pinnedOlderTopic.setPinned(true);
        pinnedOlderTopic.touch(olderActivity);
        topics.saveAndFlush(pinnedOlderTopic);
        Topic newerTopic = new Topic(category, author, "Newest activity topic");
        newerTopic.touch(newerActivity);
        topics.saveAndFlush(newerTopic);

        assertThat(forum.homeCategories()).singleElement()
                .extracting(categorySummary -> categorySummary.lastActivityAt())
                .isEqualTo(newerActivity);
    }

    @Test
    void concurrentCrossDemotionCannotRemoveLastAdministrator() throws Exception {
        User administratorA = createUser("admin-a@example.test", "admin-a", true, Role.ADMIN);
        User administratorB = createUser("admin-b@example.test", "admin-b", true, Role.ADMIN);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstTransactionLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        moderation.updateUser(
                                administratorA.getId(), administratorB.getId(), demotionForm());
                        users.flush();
                        firstTransactionLocked.countDown();
                        await(releaseFirstTransaction);
                    }));
            assertThat(firstTransactionLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> moderation.updateUser(
                    administratorB.getId(), administratorA.getId(), demotionForm()));
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirstTransaction.countDown();
            first.get(10, TimeUnit.SECONDS);
            Throwable secondFailure = catchThrowable(() -> second.get(10, TimeUnit.SECONDS));
            assertThat(secondFailure).isInstanceOf(ExecutionException.class);
            assertThat(secondFailure.getCause())
                    .isInstanceOfAny(BusinessRuleException.class, AccessDeniedException.class);
            assertThat(users.countByRolesContaining(Role.ADMIN)).isOne();
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentRepliesKeepCounterExact() throws Exception {
        User firstAuthor = createUser("reply-a@example.test", "reply-a", true);
        User secondAuthor = createUser("reply-b@example.test", "reply-b", true);
        TopicFixture fixture = createTopic(firstAuthor, createCategory());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstTransactionLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        forum.createReply(firstAuthor.getId(), fixture.topic().getId(),
                                replyForm("First concurrent reply"));
                        topics.flush();
                        firstTransactionLocked.countDown();
                        await(releaseFirstTransaction);
                    }));
            assertThat(firstTransactionLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> forum.createReply(
                    secondAuthor.getId(), fixture.topic().getId(),
                    replyForm("Second concurrent reply")));
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirstTransaction.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(posts.countByTopicIdAndInitialPostFalseAndStatus(
                    fixture.topic().getId(), PostStatus.VISIBLE)).isEqualTo(2);
            assertThat(topics.findById(fixture.topic().getId()).orElseThrow().getReplyCount())
                    .isEqualTo(2);
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentLikesKeepCountersExact() throws Exception {
        User firstMember = createUser("like-a@example.test", "like-a", true);
        User secondMember = createUser("like-b@example.test", "like-b", true);
        TopicFixture fixture = createTopic(firstMember, createCategory());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstTransactionLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        interactions.toggleLike(firstMember.getId(), fixture.initialPost().getId());
                        likes.flush();
                        firstTransactionLocked.countDown();
                        await(releaseFirstTransaction);
                    }));
            assertThat(firstTransactionLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> interactions.toggleLike(
                    secondMember.getId(), fixture.initialPost().getId()));
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirstTransaction.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(likes.countByPostId(fixture.initialPost().getId())).isEqualTo(2);
            assertThat(posts.findById(fixture.initialPost().getId()).orElseThrow().getLikeCount())
                    .isEqualTo(2);
            assertThat(topics.findById(fixture.topic().getId()).orElseThrow().getLikeCount())
                    .isEqualTo(2);
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentReportResolutionAllowsExactlyOneDecision() throws Exception {
        User author = createUser("report-author@example.test", "report-author", true);
        User reporter = createUser("reporter-race@example.test", "reporter-race", true);
        User firstModerator = createUser(
                "moderator-a@example.test", "moderator-a", true, Role.MODERATOR);
        User secondModerator = createUser(
                "moderator-b@example.test", "moderator-b", true, Role.MODERATOR);
        Category category = createCategory();
        TopicFixture fixture = createTopic(author, category);
        moderatorCategories.saveAndFlush(new ModeratorCategory(firstModerator, category, firstModerator));
        moderatorCategories.saveAndFlush(new ModeratorCategory(secondModerator, category, firstModerator));
        Report report = reports.saveAndFlush(new Report(
                reporter, fixture.initialPost(), "A report resolved concurrently"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstTransactionLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        moderation.resolveReport(
                                firstModerator.getId(), report.getId(), rejectionForm());
                        reports.flush();
                        firstTransactionLocked.countDown();
                        await(releaseFirstTransaction);
                    }));
            assertThat(firstTransactionLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> moderation.resolveReport(
                    secondModerator.getId(), report.getId(), rejectionForm()));
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirstTransaction.countDown();
            first.get(10, TimeUnit.SECONDS);
            Throwable secondFailure = catchThrowable(() -> second.get(10, TimeUnit.SECONDS));
            assertThat(secondFailure).isInstanceOf(ExecutionException.class);
            assertThat(secondFailure.getCause())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("report.alreadyResolved");
            Report resolved = reports.findById(report.getId()).orElseThrow();
            assertThat(resolved.getStatus()).isEqualTo(ReportStatus.REJECTED);
            assertThat(resolved.getResolvedBy().getId()).isEqualTo(firstModerator.getId());
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM audit_log
                     WHERE target_type = 'Report'
                       AND target_id = ?
                       AND action IN ('REPORT_REJECTED', 'REPORT_RESOLVED')
                    """, Long.class, report.getId())).isOne();
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentFirstBookmarkAndSubscriptionSerializeOnTopic() throws Exception {
        User member = createUser("interaction-race@example.test", "interaction-race", true);
        TopicFixture fixture = createTopic(member, createCategory());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstTransactionLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .execute(status -> {
                        boolean added = interactions.toggleBookmark(
                                member.getId(), fixture.topic().getId());
                        bookmarks.flush();
                        firstTransactionLocked.countDown();
                        await(releaseFirstTransaction);
                        return added;
                    }));
            assertThat(firstTransactionLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> second = executor.submit(() -> interactions.toggleSubscription(
                    member.getId(), fixture.topic().getId()));
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirstTransaction.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(bookmarks.findByUserIdAndTopicId(
                    member.getId(), fixture.topic().getId())).isPresent();
            assertThat(subscriptions.findByUserIdAndTopicId(
                    member.getId(), fixture.topic().getId())).isPresent();
            assertThat(bookmarks.count()).isOne();
            assertThat(subscriptions.count()).isOne();
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }
    }

    private void toggleTopicInteraction(String action, Long topicId, FormaldaPrincipal principal) throws Exception {
        mvc.perform(post("/topics/{id}/" + action, topicId)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics/" + topicId));
    }

    private Forms.Reply replyForm(String body) {
        Forms.Reply form = new Forms.Reply();
        form.setBody(body);
        return form;
    }

    private Forms.UserAdministration demotionForm() {
        Forms.UserAdministration form = new Forms.UserAdministration();
        form.setRoles(Set.of(Role.USER.name()));
        form.setReason("Concurrent administrator demotion test");
        return form;
    }

    private Forms.ModerationDecision rejectionForm() {
        Forms.ModerationDecision form = new Forms.ModerationDecision();
        form.setAction("REJECT");
        form.setReason("Concurrent moderation decision");
        return form;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrent test was interrupted", exception);
        }
    }

    private User createUser(String email, String username, boolean verified, Role... additionalRoles) {
        User user = new User(email, username, passwordEncoder.encode(PASSWORD));
        if (verified) {
            user.verifyEmail();
        }
        for (Role role : additionalRoles) {
            user.addRole(role);
        }
        return users.saveAndFlush(user);
    }

    private Category createCategory() {
        return categories.saveAndFlush(new Category(
                "integration-tests", "Integration tests", "Category used by integration tests", 1));
    }

    private TopicFixture createTopic(User author, Category category) {
        Topic topic = topics.saveAndFlush(new Topic(category, author, "Fixture topic"));
        Post initialPost = posts.saveAndFlush(
                new Post(topic, author, "Fixture initial body", true, null));
        return new TopicFixture(topic, initialPost);
    }

    private record TopicFixture(Topic topic, Post initialPost) {
    }
}
