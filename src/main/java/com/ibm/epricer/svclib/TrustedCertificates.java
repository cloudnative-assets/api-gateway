package com.ibm.epricer.svclib;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

/**
 * This event listener checks if any trusted certificates are specified via spring properties, then
 * builds JKS trust store and saves it to a predetermined location. Otherwise makes sure a pre-built
 * trust store exists. Location of the trust store is recorded in the environment.
 * 
 * Certificates are allowed to miss prefix and suffix.
 * 
 * Extracting x.509 certificate from JKS:
 * 
 * <pre>
 * keytool.exe -export -rfc -alias tpydalepdb401 -file tpydalepdb401.crt -keystore epricertrust.jks
 * </pre>
 * 
 * @author Kiran Chowdhury
 */
public class TrustedCertificates implements ApplicationListener<ApplicationPreparedEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(TrustedCertificates.class);

    private static final String PREFIX = "epricer.trustcert.";
    private static final String KEY = "epricer.ssl-truststore-location";
    private static final String TRUSTSTORE = "data/epricertrust.jks";
    private static final String X509_PREFIX = "-----BEGIN CERTIFICATE-----";
    private static final String X509_SUFFIX = "-----END CERTIFICATE-----";

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        LOG.debug("Configuring DB trust store...");
        ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();
        MutablePropertySources sources = env.getPropertySources();
        sources.addFirst(new MapPropertySource("my-trustcert-location", Collections.singletonMap(KEY, TRUSTSTORE)));
        try {
            handle(env);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            throw new IllegalStateException("Could not create DB trust store from properties", e);
        }
    }

    private void handle(ConfigurableEnvironment env)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        KeyStore ks = KeyStore.getInstance("jks");
        ks.load(null, null);

        boolean found = false;
        for (int i = 0;; i++) {
            String variable = PREFIX.toLowerCase() + i;
            String trustCert = env.getProperty(variable); // System.getenv(variable);
            if (trustCert == null) {
                variable = variable.toUpperCase();
                trustCert = env.getProperty(variable); // System.getenv(variable);
                if (trustCert == null) {
                    break; // no more variables
                }
            }

            if (!trustCert.startsWith(X509_PREFIX)) {
                trustCert = X509_PREFIX + "\n" + trustCert.strip() + "\n" + X509_SUFFIX;
            }

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(trustCert.getBytes()));
            Certificate cert = cf.generateCertificate(bis);
            ks.setCertificateEntry("" + i, cert); // aliases are 0, 1, etc...
            LOG.info("Imported DB trust certificate from OS variable {}", variable);
            found = true;
        }

        if (found) {
            try (FileOutputStream fos = new FileOutputStream(TRUSTSTORE)) {
                ks.store(fos, "".toCharArray());
                LOG.info("Saved DB trust store to {}", TRUSTSTORE);
            }
        } else {
            LOG.info("Trusted certifivcates are not specified in the properties");
            if (Files.notExists(Paths.get(TRUSTSTORE))) {
                LOG.warn("DB trust store is not found at {}", TRUSTSTORE);
            }
        }
    }
}
