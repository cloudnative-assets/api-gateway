package com.ibm.epricer.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import com.ibm.epricer.svclib.TrustedCertificates;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(GatewayApplication.class);
        SpringApplication app = builder.build();
        app.addListeners(new TrustedCertificates());
        app.run(args);
    }

}
