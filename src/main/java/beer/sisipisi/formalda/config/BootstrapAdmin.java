package beer.sisipisi.formalda.config;

import beer.sisipisi.formalda.entity.Role;
import beer.sisipisi.formalda.entity.User;
import beer.sisipisi.formalda.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class BootstrapAdmin implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final FormaldaProperties properties;

    public BootstrapAdmin(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            FormaldaProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (users.existsByRolesContaining(Role.ADMIN)) {
            return;
        }

        FormaldaProperties.BootstrapAdmin configuredAdmin = properties.bootstrapAdmin();
        if (configuredAdmin == null || !configuredAdmin.configured()) {
            log.warn("No administrator exists and FORMALDA_ADMIN_* variables are not fully configured");
            return;
        }

        String email = configuredAdmin.email().strip().toLowerCase(Locale.ROOT);
        String username = configuredAdmin.username().strip();
        if (users.existsByEmailIgnoreCase(email) || users.existsByUsernameIgnoreCase(username)) {
            throw new IllegalStateException(
                    "Cannot create the bootstrap administrator: email or username already belongs to another account");
        }

        User administrator = new User(email, username, passwordEncoder.encode(configuredAdmin.password()));
        administrator.verifyEmail();
        administrator.addRole(Role.ADMIN);
        users.saveAndFlush(administrator);
        log.info("Created the initial Formalda administrator account: {}", username);
    }
}
