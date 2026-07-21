package com.banco.cuentas.infrastructure.out.salesforce;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

import java.util.List;
import java.util.Map;

public class SalesforceExceptionMapper  implements ResponseExceptionMapper<SalesforceApiException> {
    @Override
    public SalesforceApiException toThrowable(Response response) {
        String code = "UNKNOWN";
        String msg = "sin detalle";
        try {
            List<Map<String, Object>> errores =
                    response.readEntity(new GenericType<>() {
                    });
            if (!errores.isEmpty()) {
                code = String.valueOf(errores.get(0).get("errorCode"));
                msg = String.valueOf(errores.get(0).get("message"));
            }
        } catch (Exception ignored) {}
            return new SalesforceApiException(response.getStatus(), code, msg);

    }

    @Override
    public boolean handles(int status, MultivaluedMap<String, Object> headers) {
        return status>=400;
    }
}
