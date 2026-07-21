package com.banco.cuentas.infrastructure;

import com.banco.cuentas.application.ConsultarCuentaUseCase;
import com.banco.cuentas.domain.port.out.ClienteCrmPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Produces;

public class UseCaseProducer {

    @Produces
    @ApplicationScoped
    ConsultarCuentaUseCase consultarCuentaUseCase(ClienteCrmPort crm){
        return new ConsultarCuentaUseCase(crm); // CDI inyecta el SalesforceCrmAdapter
    }

}
