package beer.sisipisi.formalda.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import beer.sisipisi.formalda.dto.Forms;
import beer.sisipisi.formalda.dto.ViewModels.AdminUserView;
import beer.sisipisi.formalda.security.FormaldaPrincipal;
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
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final ModerationService moderation;
    private final Clock clock;

    public AdminController(ModerationService moderation, Clock clock) {
        this.moderation = moderation;
        this.clock = clock;
    }

    @GetMapping("/admin/categories")
    public String categories(
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        model.addAttribute("categories", moderation.allCategories(principal.id(), page));
        return "admin/categories";
    }

    @GetMapping("/admin/categories/new")
    public String newCategory(Model model) {
        model.addAttribute("form", new Forms.Category());
        model.addAttribute("editing", false);
        return "admin/category-form";
    }

    @PostMapping("/admin/categories/new")
    public String createCategory(
            @Valid @ModelAttribute("form") Forms.Category form,
            BindingResult binding,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("editing", false);
            return "admin/category-form";
        }
        moderation.createCategory(principal.id(), form);
        redirect.addFlashAttribute("success", "flash.categoryCreated");
        return "redirect:/admin/categories";
    }

    @GetMapping("/admin/categories/{id}/edit")
    public String editCategory(
            @PathVariable Long id,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        model.addAttribute("categoryId", id);
        model.addAttribute("form", moderation.categoryForm(principal.id(), id));
        model.addAttribute("editing", true);
        return "admin/category-form";
    }

    @PostMapping("/admin/categories/{id}/edit")
    public String updateCategory(
            @PathVariable Long id,
            @Valid @ModelAttribute("form") Forms.Category form,
            BindingResult binding,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("categoryId", id);
            model.addAttribute("editing", true);
            return "admin/category-form";
        }
        moderation.updateCategory(principal.id(), id, form);
        redirect.addFlashAttribute("success", "flash.categoryUpdated");
        return "redirect:/admin/categories";
    }

    @GetMapping("/admin/users")
    public String users(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        model.addAttribute("query", query);
        model.addAttribute("users", moderation.users(principal.id(), query, page));
        return "admin/users";
    }

    @GetMapping("/admin/users/{id}")
    public String user(
            @PathVariable Long id,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        AdminUserView user = moderation.user(principal.id(), id);
        model.addAttribute("user", user);
        model.addAttribute("form", moderation.userAdministrationForm(principal.id(), id));
        model.addAttribute("categories", moderation.allCategoryOptionsForAdministration(principal.id()));
        return "admin/user-detail";
    }

    @PostMapping("/admin/users/{id}")
    public String updateUser(
            @PathVariable Long id,
            @Valid @ModelAttribute("form") Forms.UserAdministration form,
            BindingResult binding,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model,
            RedirectAttributes redirect) {
        validateBlocking(form, binding);
        if (binding.hasErrors()) {
            model.addAttribute("user", moderation.user(principal.id(), id));
            model.addAttribute("categories", moderation.allCategoryOptionsForAdministration(principal.id()));
            return "admin/user-detail";
        }
        moderation.updateUser(principal.id(), id, form);
        redirect.addFlashAttribute("success", "flash.userUpdated");
        return "redirect:/admin/users/" + id;
    }

    private void validateBlocking(Forms.UserAdministration form, BindingResult binding) {
        if (!form.isBlocked()) {
            return;
        }
        if (form.getReason() == null || form.getReason().isBlank()) {
            binding.rejectValue("reason", "admin.blockReasonRequired");
        }
        if (form.getBlockedUntil() != null
                && !form.getBlockedUntil().toInstant(ZoneOffset.UTC).isAfter(Instant.now(clock))) {
            binding.rejectValue("blockedUntil", "admin.blockUntilFuture");
        }
    }

}
