package com.banco.cuentas.infrastructure.out.salesforce;

import com.banco.cuentas.domain.exception.CrmNodisponibleException;
import com.banco.cuentas.domain.model.Cuenta;
import com.banco.cuentas.domain.model.EstadoCuenta;
import com.banco.cuentas.domain.port.out.ClienteCrmPort;
import com.banco.cuentas.infrastructure.out.salesforce.dto.AccountDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class SalesforceCrmAdapter implements ClienteCrmPort {

    private final SalesforceClient sf;
    private final SalesforceTokenService tokenService;

    @Inject
    public SalesforceCrmAdapter(
            @RestClient SalesforceClient sf,
            SalesforceTokenService tokenService) {
        this.sf = sf;
        this.tokenService = tokenService;
    }

    @Override
    @Retry(maxRetries = 2, delay = 500, jitter = 200,
            retryOn = { CrmNodisponibleException.class })
    @Timeout(8_000)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 15_000)
    public Optional<Cuenta> buscarPorId(String idCrm) {
        try {
            return Optional.of(aDominio(conReintento401(() -> sf.getAccount(idCrm))));
        } catch (SalesforceApiException e) {
            if (e.status() == 404) return Optional.empty();      // caso de negocio
            throw traducir(e);                                    // técnico
        } catch (ProcessingException e) {                         // timeout/conexión
            throw new CrmNodisponibleException("Fallo de conexión con CRM", e);
        }
    }

    @Override
    @Retry(maxRetries = 2, delay = 500, retryOn = { CrmNodisponibleException.class })
    @Timeout(8_000)
    public List<Cuenta> listar(int limite) {
        try {
            String soql = "SELECT Id, Name, AccountNumber, Estado_Cliente__c "
                    + "FROM Account LIMIT " + limite;

            List<Cuenta> resultado = new java.util.ArrayList<>();

            var pagina = conReintento401(() -> sf.query(soql));
            resultado.addAll(pagina.records().stream().map(this::aDominio).toList());

            while (!pagina.done()) {
                final int offset = resultado.size();
                pagina = conReintento401(() -> sf.queryMore(soql, offset));
                resultado.addAll(pagina.records().stream().map(this::aDominio).toList());
            }

            return resultado;
        } catch (SalesforceApiException e) {
            throw traducir(e);
        } catch (ProcessingException e) {
            throw new CrmNodisponibleException("Fallo de conexión con CRM", e);
        }
    }

    @Override
    // OJO: PATCH idempotente (mismo estado final) → seguro reintentar.
    // Un POST de creación NO llevaría @Retry sin idempotency key.
    @Retry(maxRetries = 1, delay = 500, retryOn = { CrmNodisponibleException.class })
    @Timeout(8_000)
    public void actualizarEstado(String idCrm, EstadoCuenta estado) {
        try {
            conReintento401(() -> {
                sf.updateAccount(idCrm, Map.of("Estado_Cliente__c", estado.name()));
                return null;
            });
        } catch (SalesforceApiException e) {
            throw traducir(e);
        } catch (ProcessingException e) {
            throw new CrmNodisponibleException("Fallo de conexión con CRM", e);
        }
    }

    // ---------- helpers ----------

    /** Ante 401 (sesión expirada en SF): invalida el token y reintenta UNA vez. */
    private <T> T conReintento401(java.util.function.Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (SalesforceApiException e) {
            if (e.status() == 401) {
                tokenService.invalidate();
                return llamada.get();
            }
            throw e;
        }
    }

    private RuntimeException traducir(SalesforceApiException e) {
        // 5xx y 429 son transitorios → excepción que dispara @Retry / abre el breaker
        if (e.status() >= 500 || e.status() == 429) {
            return new CrmNodisponibleException(e.getMessage(), e);
        }
        return e;  // 4xx de negocio: propagar sin reintentos
    }

    private Cuenta aDominio(AccountDto dto) {
        EstadoCuenta estado;
        try {
            estado = dto.estadoCliente() == null
                    ? EstadoCuenta.DESCONOCIDO
                    : EstadoCuenta.valueOf(dto.estadoCliente());
        } catch (IllegalArgumentException ex) {
            estado = EstadoCuenta.DESCONOCIDO;   // anticorrupción: valores raros del CRM
        }
        return new Cuenta(dto.id(), dto.name(), dto.accountNumber(), estado);
    }
}
// touch 1784740906
