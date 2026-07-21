package com.banco.cuentas.infrastructure.out.salesforce;

/** Excepción técnica del adaptador, con el status HTTP y errorCode de Salesforce.**/
public class SalesforceApiException extends RuntimeException{
    private final int status;
    private final String errorCode;

    public  SalesforceApiException(int status, String errorCode, String message) {
        super("[SF " +  status+ "/"+ errorCode + "] " + message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int status() { return status; }
    public String errorCode() { return errorCode; }

}
