package beer.sisipisi.formalda.controller;

import beer.sisipisi.formalda.dto.Forms;
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
@PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
public class ModerationController {
    private final ModerationService moderation;

    public ModerationController(ModerationService moderation) {
        this.moderation = moderation;
    }

    @GetMapping("/moderation/reports")
    public String queue(
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        model.addAttribute("reports", moderation.reportQueue(principal.id(), page));
        return "moderation/queue";
    }

    @GetMapping("/moderation/reports/{id}")
    public String report(
            @PathVariable Long id,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model) {
        model.addAttribute("report", moderation.report(principal.id(), id));
        model.addAttribute("form", new Forms.ModerationDecision());
        return "moderation/detail";
    }

    @PostMapping("/moderation/reports/{id}")
    public String resolve(
            @PathVariable Long id,
            @Valid @ModelAttribute("form") Forms.ModerationDecision form,
            BindingResult binding,
            @AuthenticationPrincipal FormaldaPrincipal principal,
            Model model,
            RedirectAttributes redirect) {
        if (!binding.hasFieldErrors("action")
                && !java.util.Set.of("REJECT", "HIDE", "BLOCK").contains(form.getAction())) {
            binding.rejectValue("action", "report.invalidAction");
        }
        if ("BLOCK".equals(form.getAction()) && form.getBlockDays() == null) {
            binding.rejectValue("blockDays", "report.blockDaysRequired");
        }
        if (binding.hasErrors()) {
            model.addAttribute("report", moderation.report(principal.id(), id));
            return "moderation/detail";
        }
        moderation.resolveReport(principal.id(), id, form);
        redirect.addFlashAttribute("success", "flash.reportResolved");
        return "redirect:/moderation/reports";
    }
}
