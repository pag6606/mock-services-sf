package com.banco.cuentas.infrastructure.in.rest;

import com.banco.cuentas.domain.exception.CrmNodisponibleException;
import com.banco.cuentas.domain.exception.CuentaNoEncontradaException;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import java.util.Map;


@Provider
public class DomainExceptionMapper implements ExceptionMapper<RuntimeException> {


    @Override
    public Response toResponse(RuntimeException e) {
        if (e instanceof CuentaNoEncontradaException){
            return Response.status(404)
                    .entity(Map.of("error", "CUENTA_NO_ENCONTRADA", "detalle", e.getMessage()))
                    .build();
        }
        // Los TRES casos de "el CRM no responde": misma respuesta 503
        if (e instanceof CrmNodisponibleException          // retries agotados
                || e instanceof CircuitBreakerOpenException // breaker abierto
                || e instanceof TimeoutException) {         // @Timeout cortó
            return Response.status(503)
                    .header("Retry-After", "15")
                    .entity(Map.of("error", "CRM_NO_DISPONIBLE",
                            "detalle", "Intente nuevamente en unos minutos"))
                    .build();
        }
        Log.error("Excepción no mapeada llegó al borde HTTP", e);
        return Response.status(500)

                .entity(Map.of("error","ERROR_INTERNO"))
                .build();
    }
}
