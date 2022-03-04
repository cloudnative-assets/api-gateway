package com.ibm.epricer.gateway.db;

import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
class PrefDataConfig {
    static final String CONFIIG_PROP_PREFIX = "epricer.datasource.pref";

    @Bean(name = "prefDataSource")
    @ConfigurationProperties(prefix = CONFIIG_PROP_PREFIX)
    public DataSource dataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }
}
