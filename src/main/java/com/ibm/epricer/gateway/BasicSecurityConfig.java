package com.ibm.epricer.gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import com.ibm.epricer.gateway.db.AuthorizedUsers;

/**
 * Development security configuration with only Basic authentication
 *
 * @author Kiran Chowdhury
 */

@Profile("development")
@EnableWebFluxSecurity
public class BasicSecurityConfig {
    @Value("${epricer.gateway.password:}")
    private String epricerSecret;

    @Autowired
    private AuthorizedUsers users;
    
    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http.httpBasic().authenticationManager(new BasicReactiveAuthenticationManager(epricerSecret, users));
        http.authorizeExchange()
            .pathMatchers(HttpMethod.POST, "/").permitAll()
            .pathMatchers(HttpMethod.POST, "/rpc").permitAll()
            .pathMatchers("/actuator/health/**").permitAll()
            .anyExchange().authenticated();
        http.csrf().disable();
        return http.build();
    }
}
