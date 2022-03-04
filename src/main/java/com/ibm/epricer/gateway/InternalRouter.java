package com.ibm.epricer.gateway;

import static io.micrometer.core.instrument.util.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.webflux.ProxyExchange;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ServerWebExchange;
import io.micrometer.core.instrument.util.StringUtils;
import reactor.core.publisher.Mono;

/**
 * Internal communication routers, only accessible to the gateway itself via forwarding, cluster
 * internal services, and localhost.
 * 
 * @author Kiran Chowdhury
 */

@RestController
@ConfigurationProperties(prefix = "epricer")
class InternalRouter {
    private static final Logger LOG = LoggerFactory.getLogger(InternalRouter.class);

    private static final String FORWARD_SCHEME = "forward";
    private static final String SVCID_HEADER = "epricer-service-id";
    private static final String EPID_HEADER = "epricer-endpoint-id";
    private static final String EPVER_HEADER = "epricer-endpoint-ver";
    private static final String PAYLOAD_HEADER = "epricer-payload";
    private static final String USERID_HEADER = "epricer-user-id";
    private static final String GROUPID_HEADER = "epricer-group-id";
    private static final String TRACEID_HEADER = "epricer-trace-id";
    private static final RuntimeException ACCESS_DENIED = new HttpClientErrorException(HttpStatus.FORBIDDEN, "Only for internal communication");
    private static final RuntimeException USER_NOT_SPECIFIED = new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Epricer user id header is not specified");

    private Map<String, String> services; // properties: static service address map
    private String serviceId; // properties: gateway service id

    @Value("${server.port}")
    private Integer serverPort; // properties: gateway local server port

    /**
     * Internal cluster communication and development. Gateway host name must match epricer.service-id
     */
    @PostMapping("/")
    Mono<ResponseEntity<byte[]>> postProxy(ServerWebExchange exchange, @RequestHeader(SVCID_HEADER) String serviceId,
            ProxyExchange<byte[]> proxy) {

        allowOnlyInternalAndForwardedCalls(exchange);

        String uri = buildServiceUri(serviceId);
        return proxy.uri(uri).post();
    }

    @GetMapping("/get-convert")
    Mono<ResponseEntity<byte[]>> convertGetToRpc(ServerWebExchange exchange,
            @RequestHeader(SVCID_HEADER) String serviceId, @RequestHeader(EPID_HEADER) String endpointId,
            @RequestHeader(name = EPVER_HEADER, required = false) Integer endpointVer,
            @RequestParam(required = false) Map<String, String> urlParameters,
            @RequestHeader(name = PAYLOAD_HEADER, required = false) String payload, Authentication auth,
            ProxyExchange<byte[]> proxy) {

        allowOnlyInternalAndForwardedCalls(exchange);

        HttpHeaders headers = createHeaders(exchange, serviceId, endpointId, endpointVer, auth);
        String uri = buildServiceUri(serviceId);
        
        /*
         * If single path parameter is defined as {param} then any url parameters are ignored. if single
         * path parameter needs to be combined with the url parameters then specify it as myparam={param}
         */
        if (payload != null && !payload.contains("=")) { // simple body
            if (urlParameters != null && urlParameters.size() >0) {
                LOG.warn("{}The url parameters are ignored: {}", exchange.getLogPrefix(), urlParameters);
            }
            LOG.debug("{}Passing single path parameter {} as JSON body", exchange.getLogPrefix(), payload);
            return proxy.headers(headers).body(payload).uri(uri).post();
        }
        
        Map<String,String> jsonBody = convertComplexBody(payload); // may return empty map
        
        if (urlParameters != null) {
            // add url parameters, but do NOT overwrite path parameters!
            urlParameters.forEach((k,v) -> jsonBody.putIfAbsent(k, v));
        }
        
        if (jsonBody.isEmpty()) {
            LOG.debug("{}Posting request without body", exchange.getLogPrefix());
            return proxy.headers(headers).uri(uri).post();
        } else {
            headers.setContentType(MediaType.APPLICATION_JSON);
            LOG.debug("{}Passing multiple parameters {} as JSON body", exchange.getLogPrefix(), jsonBody);
            return proxy.headers(headers).body(jsonBody).uri(uri).post();
        }
    }

    /**
     * Returns an empty map if the body is null or empty, otherwise parses it into a map
     */
    private Map<String,String> convertComplexBody(String stringBody) {
        if (isBlank(stringBody)) {
            return new HashMap<>();
        } else {
            return Arrays.stream(stringBody.split("&"))
                    .collect(Collectors.toMap(p -> substringBefore(p, "="), p -> substringAfter(p, "=")));
        }
    }
    
    @PostMapping("/post-convert") // also can convert PUTs
    Mono<ResponseEntity<byte[]>> convertPostToRpc(ServerWebExchange exchange,
            @RequestHeader(SVCID_HEADER) String serviceId, @RequestHeader(EPID_HEADER) String endpointId,
            @RequestHeader(name = EPVER_HEADER, required = false) Integer endpointVer,
            Authentication auth, ProxyExchange<byte[]> proxy) {

        allowOnlyInternalAndForwardedCalls(exchange);
        
        HttpHeaders headers = createHeaders(exchange, serviceId, endpointId, endpointVer, auth);
        String uri = buildServiceUri(serviceId);
        
        return proxy.headers(headers).uri(uri).post();
    }

    private HttpHeaders createHeaders(ServerWebExchange exchange, String svcId, String endpointId, Integer endpointVer, 
            Authentication auth) {
        HttpHeaders headers = new HttpHeaders(); 
        // headers from the client request are not passed on to the services,
        // only the following headers are understood by the services
        headers.set(SVCID_HEADER, svcId);
        headers.set(EPID_HEADER, endpointId);
        headers.set(EPVER_HEADER, (endpointVer != null) ? String.valueOf(endpointVer) : "1");
        headers.set(TRACEID_HEADER, exchange.getLogPrefix().replace("[", "").replace("]", "").strip());
        // check for null because there may be services open for anonymous users without authentication
        if (auth != null) {
            if (auth.getAuthorities().contains(new SimpleGrantedAuthority("SCOPE_superuser"))) {
                String userId = exchange.getRequest().getHeaders().getFirst("epricer-user-id");
                if (StringUtils.isBlank(userId)) {
                    LOG.error("{}Request does not have epricer user id header", exchange.getLogPrefix());
                    throw USER_NOT_SPECIFIED;
                }
                headers.set(USERID_HEADER, userId);
            } else {
                headers.set(USERID_HEADER, auth.getName());
            }
            String groupId = exchange.getRequest().getHeaders().getFirst(GROUPID_HEADER);
            if(null != groupId) {
                headers.set(GROUPID_HEADER, groupId);
            }
        }
        return headers;
    }
    
    private void allowOnlyInternalAndForwardedCalls(ServerWebExchange exchange) {
        URI uri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        String host = exchange.getRequest().getHeaders().getFirst("Host");
        boolean internalCall = host.equalsIgnoreCase(this.serviceId) || host.equalsIgnoreCase("localhost:" + serverPort);
        boolean forwardedCall = (uri != null && FORWARD_SCHEME.equals(uri.getScheme()));
        if (!internalCall && !forwardedCall) {
            String callerAddress = exchange.getRequest().getRemoteAddress().getHostString();
            LOG.error("{}Illegal access to {} from {},{}", exchange.getLogPrefix(), host, uri, callerAddress);
            throw ACCESS_DENIED; // Host=${epricer.service-id}, localhost:${server.port}
        }
    }

    @ExceptionHandler(HttpClientErrorException.class)
    ResponseEntity<String> customException(HttpClientErrorException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(exception.getMessage());
    }

    private String buildServiceUri(String serviceId) {
        // First look if the service id is mapped to URI explicitly
        if (services != null) {
            String location = defaultIfBlank(services.get(serviceId), services.get(serviceId.replace("-", "")));
            if (isNotBlank(location)) {
                return location; // found service URI in the static map
            }
        }
        // otherwise build Cirrus-specific URI
        return "http://" + serviceId + ":80/rpc";
    }

    Map<String, String> getServices() {
        return services;
    }

    void setServices(Map<String, String> services) {
        this.services = services;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }
}
