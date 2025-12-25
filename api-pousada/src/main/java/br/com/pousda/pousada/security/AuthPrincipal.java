package br.com.pousda.pousada.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Principal;
import java.util.Collection;
import java.util.Collections;

@Getter
@AllArgsConstructor
public class AuthPrincipal implements Principal, UserDetails {
    private final Long id;
    private final String username;
    private final String email;
    private final String role;
    private final boolean active;

    @Override
    public String getName() {
        return (username != null && !username.isBlank())
                ? username
                : (id != null ? String.valueOf(id) : "");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String authority = (role != null && role.startsWith("ROLE_")) ? role : "ROLE_" + role;
        return Collections.singletonList(new SimpleGrantedAuthority(authority));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public String toString() {
        return getName();
    }
}