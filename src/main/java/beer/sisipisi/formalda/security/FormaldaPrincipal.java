package beer.sisipisi.formalda.security;

import java.util.Collection;
import java.util.List;

import beer.sisipisi.formalda.entity.Role;
import beer.sisipisi.formalda.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record FormaldaPrincipal(
        Long id,
        String email,
        String displayUsername,
        String password,
        boolean enabled,
        List<GrantedAuthority> authorities) implements UserDetails {

    public static FormaldaPrincipal from(User user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(Role::name)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .map(GrantedAuthority.class::cast)
                .toList();
        return new FormaldaPrincipal(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getPasswordHash(),
                user.isEnabled() && user.isEmailVerified(),
                authorities);
    }

    @Override
    public String getUsername() {
        return displayUsername;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
