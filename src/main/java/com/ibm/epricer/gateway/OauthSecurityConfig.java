package com.ibm.epricer.gateway;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import com.ibm.epricer.gateway.db.AuthorizedUsers;
import reactor.core.publisher.Mono;

/**
 * Production security configuration. JWT authentication is default and always enabled. Basic
 * authentication is enabled if password is configured.
 * 
 * Actuator requires different security Defining SecurityWebFilterChain bean does not disable the
 * UserDetailsService configuration or Actuator’s security. all actuators other than /health and
 * /info are secured by default.
 * 
 * For testing, spring.security.oauth2.resourceserver.jwt.public-key-location property, where the
 * value needs to point to a file containing the public key in the PEM-encoded x509 format.
 * 
 * @author Kiran Chowdhury
 */

@Profile("default")
@EnableWebFluxSecurity
public class OauthSecurityConfig {
    private static final Logger LOG = LoggerFactory.getLogger(OauthSecurityConfig.class);

    private static final String EMAIL_CLAIM = "attributes.email";

    @Value("${epricer.gateway.oauth.trusted-aud}") // trusted audiences
    private String trustedAud;
    @Value("${epricer.gateway.oauth.impersonator-aud:}") // superuser audience
    private String impersonatorAud;

    @Value("${epricer.gateway.password:}")
    private String epricerSecret;
    
    @Autowired
    private AuthorizedUsers users;
    
    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http.authorizeExchange()
            .pathMatchers(HttpMethod.POST, "/").permitAll()
            .pathMatchers(HttpMethod.POST, "/rpc").permitAll()
            .pathMatchers("/actuator/health/**").permitAll()
            .anyExchange().authenticated();
        http.csrf().disable();
        http.oauth2ResourceServer().jwt().jwtAuthenticationConverter(this::convert);
        
        if (isNotBlank(epricerSecret)) {
            LOG.info("Basic authentication with password is ENABLED");
            http.httpBasic().authenticationManager(new BasicReactiveAuthenticationManager(epricerSecret, users));
        } else {
            LOG.warn("Basic authentication is DISABLED because password is not configured");
        }
        
        return http.build();
    }

    private Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        return Mono.just(jwt).map(this::convertJwtToAuthentication);
    }

    private AbstractAuthenticationToken convertJwtToAuthentication(Jwt jwt) {
        List<String> authorizedAuds = Arrays.asList(trustedAud.split(","));
        List<String> superusers = Arrays.asList(impersonatorAud.split(","));
        List<String> tokenAud = jwt.getAudience();
        
        // The token's audience claim has to have at least one of the allowed clients 
        if (!authorizedAuds.stream().map(String::trim).anyMatch(tokenAud::contains)) {
            LOG.warn("The required audience is missing in the token aud claim: {}", jwt);
            return new JwtAuthenticationToken(jwt); // return unauthenticated token
        }

        // If this client is in the trusted application list, user id will be extracted from request headers 
        if (superusers.stream().map(String::trim).anyMatch(tokenAud::contains)) {
            Collection<GrantedAuthority> authorities = Collections.singleton(new SimpleGrantedAuthority("SCOPE_superuser"));
            LOG.info("Application '{}' is authenticated and authorized with JWT", tokenAud);
            return new JwtAuthenticationToken(jwt, authorities);
        }
        
        String userId = jwt.getClaimAsString(EMAIL_CLAIM);
        
        // The user's e-mail must be in the list of authorized users
        if (isBlank(userId) || !users.isAuthorized(userId)) {
            LOG.warn("The user '{}' is not authorized", userId);
            return new JwtAuthenticationToken(jwt); // return unauthenticated token
        }

        LOG.info("User '{}/{}' is authenticated and authorized with JWT, aud {}", tokenAud, userId);
        Collection<GrantedAuthority> authorities = Collections.singleton(new SimpleGrantedAuthority("SCOPE_user"));
        return new JwtAuthenticationToken(jwt, authorities, userId.toLowerCase());
    }
}
