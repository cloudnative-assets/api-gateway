package com.ibm.epricer.gateway.db;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class PrefJdbcTemplate extends JdbcTemplate {

    @Autowired
    void init(DataSource dataSource) {
        this.setDataSource(dataSource);
    }

}
