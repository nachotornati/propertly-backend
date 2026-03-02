package com.propertly.model;

import java.time.LocalDateTime;

public class User {
    public String id;
    public String username;
    public String passwordHash;
    public String agencyId;
    public LocalDateTime createdAt;

    public User() {}
}
