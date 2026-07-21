package com.banco.cuentas.infrastructure.out.salesforce;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "app.salesforce")
public interface SalesfoceConfig {
    String apiVersion();
    int tokenTtlMinutes();
    Auth auth();

    interface Auth{
        String clientId();
        String username();
        String audience();
    }
}
