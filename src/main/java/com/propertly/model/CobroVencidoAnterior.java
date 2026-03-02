package com.propertly.model;

import java.time.LocalDate;

public class CobroVencidoAnterior {
    public Property property;
    public LocalDate mes;
    public Cobro cobro; // null = no cobro registered; non-null = cobro exists but unpaid

    public CobroVencidoAnterior(Property property, LocalDate mes, Cobro cobro) {
        this.property = property;
        this.mes = mes;
        this.cobro = cobro;
    }
}
