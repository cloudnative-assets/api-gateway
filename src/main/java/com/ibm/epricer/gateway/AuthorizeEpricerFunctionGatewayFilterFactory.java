package com.ibm.epricer.gateway;

import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
class AuthorizeEpricerFunctionGatewayFilterFactory
        extends AbstractGatewayFilterFactory<AuthorizeEpricerFunctionGatewayFilterFactory.Config> {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorizeEpricerFunctionGatewayFilterFactory.class);

    public static final String FUNCTION_KEY = "function";
    
    AuthorizeEpricerFunctionGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList(FUNCTION_KEY);
    }
    
    @Override
    public GatewayFilter apply(Config config) {
        String function = config.function;
        return (exchange, chain) -> {
            LOG.info("++++++++++++++++++++++++++++ User is authorized to use '{}' function", function);
            //If you want to build a "pre" filter you need to manipulate the request before calling chain.filter
            ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
            //Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            //String currentPrincipalName = authentication.getName();
            //System.out.println(exchange.getPrincipal().block());   
            //use builder to manipulate the request
            return chain.filter(exchange.mutate().request(builder.build()).build());
        };
    }

    static class Config {
        private String function;

        public String getFunction() {
            return function;
        }

        public void setFunction(String function) {
            this.function = function;
        }
    }
}
