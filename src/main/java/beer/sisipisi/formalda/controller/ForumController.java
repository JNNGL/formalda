package beer.sisipisi.formalda.controller;

import beer.sisipisi.formalda.dto.Forms;
import beer.sisipisi.formalda.dto.ViewModels.PostLocation;
import beer.sisipisi.formalda.dto.ViewModels.TopicPage;
import beer.sisipisi.formalda.dto.ViewModels.TopicSort;
import beer.sisipisi.formalda.dto.ViewModels.TopicStatusFilter;
import beer.sisipisi.formalda.security.FormaldaPrincipal;
import beer.sisipisi.formalda.service.ForumService;
import beer.sisipisi.formalda.service.InteractionService;
import beer.sisipisi.formalda.service.ModerationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ForumController {
    private final ForumService forum;
    private final InteractionService interactions;
    private final ModerationService moderation;

    public ForumController(
            ForumService forum,
            InteractionService interactions,
            ModerationService moderation) {
        this.forum = forum;
        this.interactions = interactions;
        this.moderation = moderation;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("categories", forum.homeCategories());
        model.addAttribute("recentTopics", forum.recentTopics());
        return "home";
    }

    @GetMapping("/categories/{id}")
    public String category(
            @PathVariable Long id,
            @RequestParam(required = false) TopicStatusFilter status,
            @RequestParam(defaultValue = "UPDATED") TopicSort sort,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        model.addAttribute("category", forum.categoryDetail(id));
        model.addAttribute("topics", forum.categoryTopics(id, status, sort, page));
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedSort", sort);
        return "categories/show";
    }

    @GetMapping("/topics/{id}")
    public String topic(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Long replyTo,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        Forms.Reply form = new Forms.Reply();
        form.setReplyToPostId(replyTo);
        model.addAttribute("form", form);
        populateTopic(model, forum.topic(id, page, principalId(principal)));
        return "topics/show";
    }

    @GetMapping("/posts/{id}")
    public String postPermalink(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean reply) {
        PostLocation location = forum.postLocation(id);
        return "redirect:" + (reply ? location.replyUrl() : location.url());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/topics/new")
    public String newTopic(
            @RequestParam(required = false) Long categoryId,
            Model model) {
        Forms.Topic form = new Forms.Topic();
        form.setCategoryId(categoryId);
        model.addAttribute("form", form);
        model.addAttribute("categories", forum.enabledCategoryOptions());
        return "topics/form";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/topics/new")
    public String createTopic(
            @Valid @ModelAttribute("form") Forms.Topic form,
            BindingResult binding,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("categories", forum.enabledCategoryOptions());
            return "topics/form";
        }
        Long topicId = forum.createTopic(principal.id(), form);
        redirect.addFlashAttribute("success", "flash.topicCreated");
        return "redirect:/topics/" + topicId;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/topics/{id}/edit")
    public String editTopic(
            @PathVariable Long id,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        model.addAttribute("topicId", id);
        model.addAttribute("form", forum.topicEditForm(principal.id(), id));
        return "topics/edit";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/topics/{id}/edit")
    public String updateTopic(
            @PathVariable Long id,
            @Valid @ModelAttribute("form") Forms.Topic form,
            BindingResult binding,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("topicId", id);
            return "topics/edit";
        }
        forum.editTopic(principal.id(), id, form);
        redirect.addFlashAttribute("success", "flash.topicUpdated");
        return "redirect:/topics/" + id;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/topics/{id}/replies")
    public String reply(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @Valid @ModelAttribute("form") Forms.Reply form,
            BindingResult binding,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            populateTopic(model, forum.topic(id, page, principal.id()));
            return "topics/show";
        }
        Long postId = forum.createReply(principal.id(), id, form);
        redirect.addFlashAttribute("success", "flash.replyCreated");
        return redirectToPost(postId);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/posts/{id}/edit")
    public String editPost(
            @PathVariable Long id,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        model.addAttribute("postId", id);
        model.addAttribute("topicId", forum.ownedPostTopicId(principal.id(), id));
        model.addAttribute("form", forum.postEditForm(principal.id(), id));
        return "topics/post-edit";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/posts/{id}/edit")
    public String updatePost(
            @PathVariable Long id,
            @Valid @ModelAttribute("form") Forms.PostEdit form,
            BindingResult binding,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model,
            RedirectAttributes redirect) {
        Long topicId = forum.ownedPostTopicId(principal.id(), id);
        if (binding.hasErrors()) {
            model.addAttribute("postId", id);
            model.addAttribute("topicId", topicId);
            return "topics/post-edit";
        }
        forum.editPost(principal.id(), id, form);
        redirect.addFlashAttribute("success", "flash.postUpdated");
        return redirectToPost(id);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/posts/{id}/delete")
    public String confirmDeletePost(
            @PathVariable Long id,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        model.addAttribute("post", forum.postForDeletion(principal.id(), id));
        model.addAttribute("topicId", forum.ownedPostTopicId(principal.id(), id));
        return "topics/delete-confirm";
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/topics/{id}/delete")
    public String confirmDeleteTopic(@PathVariable Long id) {
        return "redirect:/posts/" + forum.initialPostId(id) + "/delete";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/posts/{id}/delete")
    public String deletePost(
            @PathVariable Long id,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            RedirectAttributes redirect) {
        forum.deletePost(principal.id(), id);
        redirect.addFlashAttribute("success", "flash.postDeleted");
        return redirectToPost(id);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/posts/{id}/like")
    public String toggleLike(
            @PathVariable Long id,
            @AuthenticationPrincipal FormaldaPrincipal principal) {
        interactions.toggleLike(principal.id(), id);
        return redirectToPost(id);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/topics/{id}/bookmark")
    public String toggleBookmark(@PathVariable Long id, @AuthenticationPrincipal FormaldaPrincipal principal) {
        interactions.toggleBookmark(principal.id(), id);
        return "redirect:/topics/" + id;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/topics/{id}/subscribe")
    public String toggleSubscription(@PathVariable Long id, @AuthenticationPrincipal FormaldaPrincipal principal) {
        interactions.toggleSubscription(principal.id(), id);
        return "redirect:/topics/" + id;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/topics/{id}/close")
    public String closeTopic(@PathVariable Long id, @AuthenticationPrincipal FormaldaPrincipal principal) {
        moderation.closeTopic(principal.id(), id);
        return "redirect:/topics/" + id;
    }

    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @PostMapping("/topics/{id}/reopen")
    public String reopenTopic(@PathVariable Long id, @AuthenticationPrincipal FormaldaPrincipal principal) {
        moderation.reopenTopic(principal.id(), id);
        return "redirect:/topics/" + id;
    }

    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @PostMapping("/topics/{id}/archive")
    public String archiveTopic(@PathVariable Long id, @AuthenticationPrincipal FormaldaPrincipal principal) {
        moderation.archiveTopic(principal.id(), id);
        return "redirect:/topics/" + id;
    }

    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @PostMapping("/topics/{id}/pin")
    public String pinTopic(@PathVariable Long id, @AuthenticationPrincipal FormaldaPrincipal principal) {
        moderation.togglePinned(principal.id(), id);
        return "redirect:/topics/" + id;
    }

    @GetMapping("/search")
    public String search(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        model.addAttribute("searchQuery", query);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("categories", forum.enabledCategoryOptions());
        model.addAttribute("topics", forum.search(query, categoryId, page));
        return "search";
    }

    private void populateTopic(Model model, TopicPage page) {
        model.addAttribute("topic", page.topic());
        model.addAttribute("posts", page.posts());
        model.addAttribute("bookmarked", page.bookmarked());
        model.addAttribute("subscribed", page.subscribed());
        model.addAttribute("canReply", page.canReply());
        model.addAttribute("canEditTopic", page.canEditTopic());
        model.addAttribute("canClose", page.canClose());
        model.addAttribute("canReopen", page.canReopen());
        model.addAttribute("canModerate", page.canModerate());
    }

    private Long principalId(FormaldaPrincipal principal) {
        return principal == null ? null : principal.id();
    }

    private String redirectToPost(Long postId) {
        return "redirect:" + forum.postLocation(postId).url();
    }
}
