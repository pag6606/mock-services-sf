package com.banco.cuentas.infrastructure.in.rest;

import com.banco.cuentas.application.ConsultarCuentaUseCase;
import com.banco.cuentas.infrastructure.in.rest.dto.CuentaResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.util.List;

@Path("/cuentas")
@Produces(MediaType.APPLICATION_JSON)
public class CuentaResource {

    private final ConsultarCuentaUseCase useCase;

    @Inject
    public CuentaResource(ConsultarCuentaUseCase useCase) {
        this.useCase = useCase;
    }

    @GET
    @Path("/{id}")
    @APIResponse(responseCode = "200", description = "Cuenta encontrada")
    @APIResponse(responseCode = "404", description = "Cuenta no encontrada en el CRM")
    @APIResponse(responseCode = "503", description = "CRM no disponible temporalmente")
    public CuentaResponse porId(@PathParam("id") String id) {
        return CuentaResponse.de(useCase.consultar(id));
    }

    @GET
    @Operation(summary = "Lista cuentas con límite configurable")
    public List<CuentaResponse> listar(@QueryParam("limite") @DefaultValue("10") int limite) {
        return useCase.listar(limite).stream().map(CuentaResponse::de).toList();
    }
}