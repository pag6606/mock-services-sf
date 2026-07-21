package com.banco.cuentas.infrastructure.out.salesforce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountDto(
    @JsonProperty("Id") String id,
    @JsonProperty("Name") String name,
    @JsonProperty("AccountNumber") String accountNumber,
    @JsonProperty("Estado_Cliente__c") String estadoCliente
){}
