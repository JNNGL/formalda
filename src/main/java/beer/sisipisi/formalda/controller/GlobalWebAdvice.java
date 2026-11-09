package beer.sisipisi.formalda.controller;

import beer.sisipisi.formalda.exception.BusinessRuleException;
import beer.sisipisi.formalda.exception.ResourceNotFoundException;
import beer.sisipisi.formalda.security.FormaldaPrincipal;
import beer.sisipisi.formalda.service.InteractionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.util.UriComponentsBuilder;

@ControllerAdvice
public class GlobalWebAdvice {
    private final InteractionService interactions;

    public GlobalWebAdvice(InteractionService interactions) {
        this.interactions = interactions;
    }

    @ModelAttribute
    public void commonAttributes(
            @AuthenticationPrincipal FormaldaPrincipal principal,
            HttpServletRequest request,
            Model model) {
        model.addAttribute("currentUser", principal);
        model.addAttribute("unreadCount", principal == null ? 0 : interactions.unreadCount(principal.id()));
        model.addAttribute("localeRuUrl", localeUrl(request, "ru"));
        model.addAttribute("localeEnUrl", localeUrl(request, "en"));
    }

    private static String localeUrl(HttpServletRequest request, String language) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        while (path.startsWith("//")) {
            path = path.substring(1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            builder.query(query);
        }
        return builder.replaceQueryParam("lang", language).build(true).toUriString();
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public String notFound(ResourceNotFoundException exception, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("errorMessage", exception.getMessage());
        return "error/404";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String forbidden(AccessDeniedException exception, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        model.addAttribute("errorMessage", exception.getMessage());
        return "error/403";
    }

    @ExceptionHandler(BusinessRuleException.class)
    public String businessRule(BusinessRuleException exception, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.CONFLICT.value());
        model.addAttribute("errorMessage", exception.getMessage());
        return "error/error";
    }
}
