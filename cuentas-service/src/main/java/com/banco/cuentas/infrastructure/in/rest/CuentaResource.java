package com.banco.cuentas.infrastructure.in.rest;

import com.banco.cuentas.application.ConsultarCuentaUseCase;
import com.banco.cuentas.infrastructure.in.rest.dto.CuentaResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/cuentas")
@Produces(MediaType.APPLICATION_JSON)
public class CuentaResource {

    @Inject
    ConsultarCuentaUseCase useCase;

    @GET
    @Path("/{id}")
    public CuentaResponse porId(@PathParam("id") String id) {
        return CuentaResponse.de(useCase.consultar(id));
    }

    @GET
    public List<CuentaResponse> listar(@QueryParam("limite") @DefaultValue("10") int limite) {
        return useCase.listar(limite).stream().map(CuentaResponse::de).toList();
    }
}