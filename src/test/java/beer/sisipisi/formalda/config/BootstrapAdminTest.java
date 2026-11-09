package beer.sisipisi.formalda.config;

import beer.sisipisi.formalda.entity.Role;
import beer.sisipisi.formalda.entity.User;
import beer.sisipisi.formalda.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminTest {
    @Mock
    private UserRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsVerifiedAdministratorWhenNoneExists() throws Exception {
        FormaldaProperties properties = new FormaldaProperties(
                "http://localhost:8080",
                null,
                new FormaldaProperties.BootstrapAdmin(
                        " Admin@Example.com ",
                        " FirstAdmin ",
                        "safe bootstrap password"));
        when(users.existsByRolesContaining(Role.ADMIN)).thenReturn(false);
        when(users.existsByEmailIgnoreCase("admin@example.com")).thenReturn(false);
        when(users.existsByUsernameIgnoreCase("FirstAdmin")).thenReturn(false);
        when(passwordEncoder.encode("safe bootstrap password")).thenReturn("encoded-password");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new BootstrapAdmin(users, passwordEncoder, properties)
                .run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(userCaptor.capture());
        User administrator = userCaptor.getValue();
        assertThat(administrator.getEmail()).isEqualTo("admin@example.com");
        assertThat(administrator.getUsername()).isEqualTo("FirstAdmin");
        assertThat(administrator.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(administrator.isEmailVerified()).isTrue();
        assertThat(administrator.getRoles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
    }
}
