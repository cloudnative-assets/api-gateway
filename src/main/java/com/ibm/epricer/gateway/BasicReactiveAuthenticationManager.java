package com.ibm.epricer.gateway;

import static org.apache.commons.lang3.StringUtils.isBlank;
import java.util.Collection;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.ibm.epricer.gateway.db.AuthorizedUsers;
import reactor.core.publisher.Mono;

/**
 * Basic authentication manager.
 *   
 * @author Kiran Chowdhury
 */

class BasicReactiveAuthenticationManager implements ReactiveAuthenticationManager {
    private static final Logger LOG = LoggerFactory.getLogger(ReactiveAuthenticationManager.class);

    private String epricerSecret;
    private AuthorizedUsers users;

    BasicReactiveAuthenticationManager(String epricerSecret, AuthorizedUsers users) {
        this.epricerSecret = epricerSecret;
        this.users = users;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication auth) {
        String userId = auth.getName();
        String password = (String) auth.getCredentials();

        if (!epricerSecret.equals(password)) {
            LOG.info("User {} is NOT authenticated", userId);
            return Mono.just(auth); // return unauthenticated token
        }

        if (isBlank(userId) || !users.isAuthorized(userId)) {
            LOG.info("User {} is NOT authorized", userId);
            return Mono.just(auth); // return unauthenticated token
        }

        LOG.info("User '{}' is authenticated and authorized with Basic token", userId);
        Collection<GrantedAuthority> authorities = Collections.singleton(new SimpleGrantedAuthority("SCOPE_user"));
        return Mono.just(new UsernamePasswordAuthenticationToken(userId.toLowerCase(), password, authorities));
    }
}
