package com.banco.cuentas.infrastructure.in.rest.dto;

import com.banco.cuentas.domain.model.Cuenta;

/** DTO de NUESTRA API: contrato propio, independiente del de Salesforce. */
public record CuentaResponse(String id, String nombre, String numero,
                             String estado, boolean operativa) {
    public static CuentaResponse de(Cuenta c) {
        return new CuentaResponse(c.idCrm(), c.nombre(), c.numeroCuenta(),
                c.estado().name(), c.operativa());
    }
}
