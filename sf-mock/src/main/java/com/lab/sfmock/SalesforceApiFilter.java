package com.lab.sfmock;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Provider
public class SalesforceApiFilter  implements ContainerRequestFilter {

    @Inject
    MockState state;


    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        String path= ctx.getUriInfo().getPath();
        // Solo protegemos la API de datos, no el token ni el panel admin
        if (path.startsWith("/")) path = path.substring(1);
        if (!path.startsWith("services/data")) return;

        // --- 1) Caos configurado ---
        switch(state.getChaos()){
            case ERROR_500->{
                ctx.abortWith(sfError(500, "UNKNOWN_EXCEPTION", "Simulated server error"));
            }
            case TIMEOUT->{
                try {Thread.sleep(30_000);}catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                }
            }
            case RATE_LIMIT->{
                ctx.abortWith(sfError(429, "RATE_LIMIT_EXCEEDED",
                        "Simulated rate limit exceeded"));
                return;
            }
            case OK->{
               /**** OK ****/
            }
        }

        // --- 2) Autenticación Bearer ---
        String auth = ctx.getHeaderString("Authorization");
        String token = (auth!=null && auth.startsWith("Bearer "))
                ? auth.substring(7) : null;
        if (token==null || !state.isTokenValido(token)) {
            ctx.abortWith(sfError(401, "INVALID_SESSION_ID", "Invalid session id"));
            return;
        }
    }

    private Response sfError(int status, String errorCode, String message) {
        return  Response.status(status)
                .entity(List.of(Map.of("message", message,
                        "errorCode", errorCode, "fields",List.of())))
                .build();
    }
}
