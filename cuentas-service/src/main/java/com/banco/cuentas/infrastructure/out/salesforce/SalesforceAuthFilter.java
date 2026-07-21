package com.banco.cuentas.infrastructure.out.salesforce;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

@ApplicationScoped
public class SalesforceAuthFilter implements ClientRequestFilter {
    @Inject
    SalesforceTokenService tokenService;

    @Override
    public void filter(ClientRequestContext ctx){
        ctx.getHeaders().putSingle("Authorization", "Bearer "+tokenService.getAccessToken());
    }
}
