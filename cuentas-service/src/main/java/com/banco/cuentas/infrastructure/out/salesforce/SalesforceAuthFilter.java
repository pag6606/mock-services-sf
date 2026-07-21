package com.banco.cuentas.infrastructure.out.salesforce;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

@ApplicationScoped
public class SalesforceAuthFilter implements ClientRequestFilter {
    private final SalesforceTokenService tokenService;

    @Inject
    public SalesforceAuthFilter(SalesforceTokenService tokenService) {
        this.tokenService = tokenService;
    }


    @Override
    public void filter(ClientRequestContext ctx){
        ctx.getHeaders().putSingle("Authorization", "Bearer "+tokenService.getAccessToken());
    }
}
