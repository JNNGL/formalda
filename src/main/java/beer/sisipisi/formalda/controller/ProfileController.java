package beer.sisipisi.formalda.controller;

import beer.sisipisi.formalda.dto.Forms;
import beer.sisipisi.formalda.exception.BusinessRuleException;
import beer.sisipisi.formalda.exception.ResourceNotFoundException;
import beer.sisipisi.formalda.security.FormaldaPrincipal;
import beer.sisipisi.formalda.service.ForumService;
import beer.sisipisi.formalda.service.InteractionService;
import beer.sisipisi.formalda.service.ProfileSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
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
public class ProfileController {
    private final ForumService forum;
    private final InteractionService interactions;
    private final ProfileSettingsService profileSettings;
    private final SecurityContextRepository securityContexts;

    public ProfileController(
            ForumService forum,
            InteractionService interactions,
            ProfileSettingsService profileSettings,
            SecurityContextRepository securityContexts) {
        this.forum = forum;
        this.interactions = interactions;
        this.profileSettings = profileSettings;
        this.securityContexts = securityContexts;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/profile")
    public String profileSettings(
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        model.addAttribute("form", profileSettings.form(principal.id()));
        return "me/profile";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/me/profile")
    public String updateProfile(
            @Valid @ModelAttribute("form") Forms.Profile form,
            BindingResult binding,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "me/profile";
        }
        FormaldaPrincipal refreshedPrincipal;
        try {
            refreshedPrincipal = profileSettings.update(principal.id(), form);
        } catch (BusinessRuleException exception) {
            binding.rejectValue("username", exception.getMessage());
            return "me/profile";
        }
        refreshAuthentication(refreshedPrincipal, request, response);
        redirect.addFlashAttribute("success", "flash.profileUpdated");
        return "redirect:/users/" + refreshedPrincipal.getUsername();
    }

    @GetMapping("/users/{username}")
    public String profile(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int topicPage,
            @RequestParam(defaultValue = "0") int postPage,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        model.addAttribute("userProfile", forum.profile(
                username, principal == null ? null : principal.id(), topicPage, postPage));
        return "profile";
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/bookmarks")
    public String bookmarks(
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        model.addAttribute("bookmarks", interactions.bookmarks(principal.id(), page));
        return "me/bookmarks";
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/subscriptions")
    public String subscriptions(
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        model.addAttribute("subscriptions", interactions.subscriptions(principal.id(), page));
        return "me/subscriptions";
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/notifications")
    public String notifications(
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        model.addAttribute("notifications", interactions.notifications(principal.id(), page));
        return "me/notifications";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/me/notifications/{id}/read")
    public String markNotificationRead(
            @PathVariable Long id,
            @AuthenticationPrincipal FormaldaPrincipal principal) {
        interactions.markNotificationRead(principal.id(), id);
        return "redirect:/me/notifications";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/me/notifications/read-all")
    public String markAllNotificationsRead(@AuthenticationPrincipal FormaldaPrincipal principal) {
        interactions.markAllNotificationsRead(principal.id());
        return "redirect:/me/notifications";
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/reports/new")
    public String reportForm(
            @RequestParam Long postId,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        Forms.Report form = new Forms.Report();
        form.setPostId(postId);
        model.addAttribute("form", form);
        model.addAttribute("post", forum.postForReport(principal.id(), postId));
        Long topicId = forum.postTopicId(postId);
        model.addAttribute("topicId", topicId);
        model.addAttribute("topic", forum.topicSummary(topicId));
        return "reports/form";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/reports/new")
    public String createReport(
            @Valid @ModelAttribute("form") Forms.Report form,
            BindingResult binding,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model,
            RedirectAttributes redirect) {
        if (form.getPostId() == null) {
            throw new ResourceNotFoundException("Post not found");
        }
        Long topicId = forum.postTopicId(form.getPostId());
        if (binding.hasErrors()) {
            model.addAttribute("post", forum.postForReport(principal.id(), form.getPostId()));
            model.addAttribute("topicId", topicId);
            model.addAttribute("topic", forum.topicSummary(topicId));
            return "reports/form";
        }
        interactions.createReport(principal.id(), form);
        redirect.addFlashAttribute("success", "flash.reportCreated");
        return "redirect:" + forum.postLocation(form.getPostId()).url();
    }

    private void refreshAuthentication(
            FormaldaPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                current == null ? null : current.getCredentials(),
                principal.getAuthorities());
        if (current != null) {
            authentication.setDetails(current.getDetails());
        }
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(authentication);
        securityContexts.saveContext(context, request, response);
    }
}
