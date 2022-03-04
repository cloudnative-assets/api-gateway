package com.ibm.epricer.gateway;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
class SetEpricerHeadersGatewayFilterFactory
        extends AbstractGatewayFilterFactory<SetEpricerHeadersGatewayFilterFactory.Config> {
    private static final Logger LOG = LoggerFactory.getLogger(SetEpricerHeadersGatewayFilterFactory.class);

    public static final String SERVICEID_KEY = "serviceId";
    public static final String ENDPOINTID_KEY = "endpointId";
    public static final String ENDPOINTVER_KEY = "endpointVer";
    public static final String PAYLOAD_KEY = "payload";

    public static final String SERVICEID_HEADER = "epricer-service-id";
    public static final String ENDPOINTID_HEADER = "epricer-endpoint-id";
    public static final String ENDPOINTVER_HEADER = "epricer-endpoint-ver";
    public static final String PAYLOAD_HEADER = "epricer-payload";

    SetEpricerHeadersGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList(SERVICEID_KEY, ENDPOINTID_KEY, ENDPOINTVER_KEY, PAYLOAD_KEY);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            LOG.info("Bilding {}", config);
            String serviceId = ServerWebExchangeUtils.expand(exchange, config.serviceId);
            String endpointId = ServerWebExchangeUtils.expand(exchange, config.endpointId);
            String endpointVer = ServerWebExchangeUtils.expand(exchange, config.endpointVer);

            String payload = null;
            if (config.payload != null) {
                payload = ServerWebExchangeUtils.expand(exchange, config.payload);
                LOG.info("Payload header created: {}", payload);
            }

            HttpHeaders newHeaders = new HttpHeaders();
            // only the following three headers are understood by the services
            newHeaders.set(SERVICEID_HEADER, serviceId);
            newHeaders.set(ENDPOINTID_HEADER, endpointId);
            newHeaders.set(ENDPOINTVER_HEADER, (StringUtils.defaultIfBlank(endpointVer, "1")));
            // plus transferring payload in a special header
            newHeaders.set(PAYLOAD_HEADER, payload);

            ServerHttpRequest request =
                    exchange.getRequest().mutate().headers(headers -> putHeaders(headers, newHeaders)).build();

            return chain.filter(exchange.mutate().request(request).build());
        };
    }

    /*
     * Adds headers from newHeaders to origHeaders if they do not exist there or replaces otherwise to
     * make sure users do not pass any epricer headers.
     */
    private void putHeaders(HttpHeaders origHeaders, HttpHeaders newHeaders) {
        newHeaders.forEach((key, value) -> origHeaders.put(key, value));
    }

    static class Config {
        private String serviceId;
        private String endpointId;
        private String endpointVer;
        private String payload;

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getEndpointId() {
            return endpointId;
        }

        public void setEndpointId(String endpointId) {
            this.endpointId = endpointId;
        }

        public String getEndpointVer() {
            return endpointVer;
        }

        public void setEndpointVer(String endpointVer) {
            this.endpointVer = endpointVer;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }

        @Override
        public String toString() {
            return String.format("Request serviceId=%s, endpointId=%s, endpointVer=%s, payload=%s", serviceId,
                    endpointId, endpointVer, payload);
        }
    }
}
