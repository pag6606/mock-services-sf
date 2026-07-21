package com.banco.cuentas.infrastructure;

import com.banco.cuentas.infrastructure.out.salesforce.SalesforceTokenService;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

public class SalesforceReadinessCheck implements HealthCheck {

    private final SalesforceTokenService tokenService;

    @Inject
    public SalesforceReadinessCheck(SalesforceTokenService tokenService) {
        this.tokenService = tokenService;
    }

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
