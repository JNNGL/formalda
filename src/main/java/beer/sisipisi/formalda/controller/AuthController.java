package beer.sisipisi.formalda.controller;

import beer.sisipisi.formalda.dto.Forms;
import beer.sisipisi.formalda.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.Objects;

@Controller
@RequestMapping("/auth")
public class AuthController {
    private final AccountService accounts;
    private final MessageSource messages;

    public AuthController(AccountService accounts, MessageSource messages) {
        this.accounts = accounts;
        this.messages = messages;
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registrationForm(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new Forms.Registration());
        }
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registrationForm") Forms.Registration form,
            BindingResult bindingResult,
            Locale locale,
            RedirectAttributes redirectAttributes) {
        if (!Objects.equals(form.getPassword(), form.getPasswordConfirmation())) {
            bindingResult.rejectValue(
                    "passwordConfirmation",
                    "auth.register.passwordMismatch",
                    message("auth.register.passwordMismatch", "Пароли не совпадают.", locale));
        }
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            AccountService.RegistrationResult result = accounts.register(form, locale);
            redirectAttributes.addAttribute("email", result.email());
            redirectAttributes.addFlashAttribute(
                    "success",
                    message(
                            "auth.register.success",
                            "Аккаунт создан. Введите код из письма.",
                            locale));
            return "redirect:/auth/verify";
        } catch (AccountService.RegistrationException exception) {
            addRegistrationError(bindingResult, exception, locale);
            return "auth/register";
        }
    }

    @GetMapping("/verify")
    public String verificationForm(
            @RequestParam(name = "email", required = false) String email,
            Model model) {
        if (!model.containsAttribute("verificationForm")) {
            Forms.Verification form = new Forms.Verification();
            form.setEmail(email);
            model.addAttribute("verificationForm", form);
        }
        return "auth/verify";
    }

    @PostMapping("/verify")
    public String verify(
            @Valid @ModelAttribute("verificationForm") Forms.Verification form,
            BindingResult bindingResult,
            Locale locale,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/verify";
        }

        AccountService.VerificationResult result = accounts.verify(form.getEmail(), form.getCode());
        if (result == AccountService.VerificationResult.VERIFIED
                || result == AccountService.VerificationResult.ALREADY_VERIFIED) {
            redirectAttributes.addFlashAttribute(
                    "success",
                    message(
                            "auth.verify.success",
                            "Почта подтверждена. Теперь можно войти.",
                            locale));
            return "redirect:/auth/login";
        }

        String key = switch (result) {
            case EXPIRED -> "auth.verify.expired";
            case ATTEMPTS_EXCEEDED -> "auth.verify.attemptsExceeded";
            default -> "auth.verify.invalid";
        };
        String fallback = switch (result) {
            case EXPIRED -> "Срок действия кода истёк. Запросите новый код.";
            case ATTEMPTS_EXCEEDED -> "Превышено число попыток. Запросите новый код.";
            default -> "Неверный код подтверждения.";
        };
        bindingResult.rejectValue("code", key, message(key, fallback, locale));
        return "auth/verify";
    }

    @PostMapping("/verify/resend")
    public String resend(
            @RequestParam("email") String email,
            Locale locale,
            RedirectAttributes redirectAttributes) {
        AccountService.ResendResult result = accounts.resend(email, locale);
        redirectAttributes.addAttribute("email", email.strip());

        switch (result.status()) {
            case SENT -> redirectAttributes.addFlashAttribute(
                    "success",
                    message(
                            "auth.verify.resent",
                            "Если аккаунт существует, новый код отправлен на почту.",
                            locale));
            case ALREADY_VERIFIED -> redirectAttributes.addFlashAttribute(
                    "message",
                    message(
                            "auth.verify.alreadyVerified",
                            "Эта почта уже подтверждена. Можно войти.",
                            locale));
            case TOO_SOON -> {
                long seconds = Math.max(1, result.retryAfter().toSeconds());
                redirectAttributes.addFlashAttribute(
                        "error",
                        message(
                                "auth.verify.tooSoon",
                                "Новый код можно запросить через " + seconds + " сек.",
                                locale,
                                seconds));
            }
        }
        return "redirect:/auth/verify";
    }

    private void addRegistrationError(
            BindingResult bindingResult,
            AccountService.RegistrationException exception,
            Locale locale) {
        switch (exception.getProblem()) {
            case EMAIL_TAKEN -> bindingResult.rejectValue(
                    "email",
                    "auth.register.emailTaken",
                    message("auth.register.emailTaken", "Этот email уже используется.", locale));
            case USERNAME_TAKEN -> bindingResult.rejectValue(
                    "username",
                    "auth.register.usernameTaken",
                    message("auth.register.usernameTaken", "Это имя пользователя уже занято.", locale));
            case PASSWORD_MISMATCH -> bindingResult.rejectValue(
                    "passwordConfirmation",
                    "auth.register.passwordMismatch",
                    message("auth.register.passwordMismatch", "Пароли не совпадают.", locale));
            case ACCOUNT_CONFLICT -> bindingResult.reject(
                    "auth.register.conflict",
                    message(
                            "auth.register.conflict",
                            "Не удалось создать аккаунт. Проверьте введённые данные.",
                            locale));
        }
    }

    private String message(String key, String fallback, Locale locale, Object... arguments) {
        return messages.getMessage(key, arguments, fallback, locale);
    }
}
