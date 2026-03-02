package com.propertly.model;

import java.time.LocalDateTime;

public class Agency {
    public String id;
    public String name;
    public String email;
    public int diasAntesRecordatorio = 30;
    public LocalDateTime createdAt;

    public Agency() {}

    public Agency(String id, String name, String email, int diasAntesRecordatorio, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.diasAntesRecordatorio = diasAntesRecordatorio;
        this.createdAt = createdAt;
    }
}
