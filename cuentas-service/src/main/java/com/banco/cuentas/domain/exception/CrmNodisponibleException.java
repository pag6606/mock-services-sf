package com.banco.cuentas.domain.exception;
/** El CRM externo no está disponible (transitorio). */
public class CrmNodisponibleException extends RuntimeException{
    public CrmNodisponibleException(String detalle, Throwable cause) {
        super(detalle,cause);

    }
}
