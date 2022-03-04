package com.ibm.epricer.gateway.db;

import static java.util.stream.Collectors.toSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

/**
 * The repository provides a set of authorized users with periodic refresh from the database
 * 
 * @author Kiran Chowdhury
 */

@Repository
@EnableScheduling
public class AuthorizedUsers {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorizedUsers.class);
    private static final String SQL_USER_EMAILS = "SELECT EMAILID FROM EPRICER.CTMAUSR WHERE RECORDTYPE='A'";

    @Autowired
    private PrefJdbcTemplate jdbc;

    private final AtomicReference<Set<String>> userCache = new AtomicReference<Set<String>>();

    AuthorizedUsers() {
        userCache.set(Collections.emptySet());
    }
    
    public boolean isAuthorized(String userId) {
        return userCache.get().contains(userId.toLowerCase());
    }

    @Scheduled(fixedDelayString = "${epricer.userset.refresh-interval:900000}") // default is 15 min 
    void refreshCache() {
        List<String> userList = jdbc.queryForList(SQL_USER_EMAILS, String.class);
        Set<String> userSet = userList.stream().map(String::trim).map(String::toLowerCase).distinct().collect(toSet());
        userCache.set(userSet);
        LOG.info("Authorized users set successfully refreshed with {} user emails", userCache.get().size());
    }
}
