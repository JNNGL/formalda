package beer.sisipisi.formalda.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "formalda")
public record FormaldaProperties(
        String baseUrl,
        Verification verification,
        BootstrapAdmin bootstrapAdmin) {

    public record Verification(Duration ttl, int maxAttempts, Duration resendCooldown) {
    }

    public record BootstrapAdmin(String email, String username, String password) {
        public boolean configured() {
            return email != null && !email.isBlank()
                    && username != null && !username.isBlank()
                    && password != null && !password.isBlank();
        }
    }
}
