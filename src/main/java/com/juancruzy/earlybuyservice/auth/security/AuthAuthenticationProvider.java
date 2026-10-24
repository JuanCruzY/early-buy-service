package com.juancruzy.earlybuyservice.auth.security;

import java.util.Set;

import com.juancruzy.earlybuyservice.auth.client.AuthClient;
import com.juancruzy.earlybuyservice.auth.dto.AuthUserResponse;
import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AuthAuthenticationProvider implements AuthenticationProvider {

    private final AuthClient authClient;

    public AuthAuthenticationProvider(AuthClient authClient) {
        this.authClient = authClient;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        AuthUserResponse response = authClient.currentUser((String) authentication.getCredentials());
        if (!response.enabled()) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }

        Set<String> permissions = response.permissions() == null
                ? Set.of() : Set.copyOf(response.permissions());
        AuthenticatedUser user = new AuthenticatedUser(response.id(), permissions);
        var authorities = permissions.stream().map(SimpleGrantedAuthority::new).toList();
        return new PreAuthenticatedAuthenticationToken(user, null, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
