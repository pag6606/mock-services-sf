package com.banco.cuentas.infrastructure;

import com.banco.cuentas.infrastructure.out.salesforce.SalesforceTokenService;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

public class SalesforceReadinessCheck implements HealthCheck {

    @Inject
    SalesforceTokenService tokenService;

    @Override
    public HealthCheckResponse call() {
        try{
            tokenService.getAccessToken();
            return HealthCheckResponse.up("Salesforce-auth");
        } catch (Exception e) {
            return HealthCheckResponse.down("salesforce-auth");
        }

    }
}
