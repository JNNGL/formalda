package beer.sisipisi.formalda.service;

import beer.sisipisi.formalda.dto.Forms;
import beer.sisipisi.formalda.entity.User;
import beer.sisipisi.formalda.exception.BusinessRuleException;
import beer.sisipisi.formalda.repository.UserRepository;
import beer.sisipisi.formalda.security.FormaldaPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileSettingsService {
    private final UserRepository users;
    private final AuthorizationService authorization;

    public ProfileSettingsService(UserRepository users, AuthorizationService authorization) {
        this.users = users;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public Forms.Profile form(Long userId) {
        User user = authorization.requireUser(userId);
        Forms.Profile form = new Forms.Profile();
        form.setUsername(user.getUsername());
        return form;
    }

    @Transactional
    public FormaldaPrincipal update(Long userId, Forms.Profile form) {
        User user = authorization.requireUser(userId);
        String username = form.getUsername().strip();
        users.findByUsernameIgnoreCase(username)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new BusinessRuleException("profile.usernameTaken");
                });
        user.changeUsername(username);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessRuleException("profile.usernameTaken");
        }
        return FormaldaPrincipal.from(user);
    }
}
