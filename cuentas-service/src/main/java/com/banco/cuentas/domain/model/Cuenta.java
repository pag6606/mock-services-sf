package com.banco.cuentas.domain.model;

import java.util.Objects;

public record Cuenta(String idCrm,
                     String nombre,
                     String numeroCuenta,
                     EstadoCuenta estado){
public  Cuenta {
    Objects.requireNonNull(idCrm, "idCrm requerido");
        Objects.requireNonNull(nombre, "nombre requerido");
        if (estado == null) estado = EstadoCuenta.DESCONOCIDO;
}
public boolean operativa(){
    return estado== EstadoCuenta.ACTIVO;
}
}
