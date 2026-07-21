package com.banco.cuentas.domain.exception;

public class CuentaNoEncontradaException extends RuntimeException{
    public CuentaNoEncontradaException(String id) {
        super("Cuenta no encontrada en CRM: "+id);

    }
}
