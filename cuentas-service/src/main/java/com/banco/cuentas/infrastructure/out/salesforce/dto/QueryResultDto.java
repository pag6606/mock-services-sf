package com.banco.cuentas.infrastructure.out.salesforce.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryResultDto( int totalSize,
                              boolean done,
                              @JsonProperty("nextRecordsUrl") String nextRecordsUrl,
                              List<AccountDto> records) {}
    