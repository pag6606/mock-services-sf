package com.lab.sfmock;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/mock-admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {
    @Inject
    MockState state;

    /** POST /mock-admin/chaos/ERROR_500 | TIMEOUT | RATE_LIMIT | OK */
    @POST
    @Path("/chaos/{modo}")
    public Map<String, String> setChaos(@PathParam("modo") MockState.ChaosMode modo) {
        state.setChaos(modo);
        return Map.of("chaos", modo.name());
    }

    /** Simula expiración de sesión: todos los tokens emitidos dejan de valer */
    @POST
    @Path("/revocar-tokens")
    public Map<String, String> revocar() {
        state.revocarTodos();
        return Map.of("status", "tokens revocados");
    }
}
