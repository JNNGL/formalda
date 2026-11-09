package beer.sisipisi.formalda.security;

import java.util.Locale;

import beer.sisipisi.formalda.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormaldaUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public FormaldaUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) {
        String normalized = identifier.strip().toLowerCase(Locale.ROOT);
        return users.findByEmailIgnoreCaseOrUsernameIgnoreCase(normalized, normalized)
                .map(FormaldaPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
    }
}
