package com.banco.cuentas.application;

import com.banco.cuentas.domain.exception.CuentaNoEncontradaException;
import com.banco.cuentas.domain.model.Cuenta;
import com.banco.cuentas.domain.port.out.ClienteCrmPort;

import java.util.List;

public class ConsultarCuentaUseCase {
    private final ClienteCrmPort crm;

    public ConsultarCuentaUseCase(ClienteCrmPort crm) {
        this.crm = crm;
    }

    public Cuenta consultar(String idCrm) {
        return crm.buscarPorId(idCrm).orElseThrow(()->new CuentaNoEncontradaException(idCrm));

    }
    public List<Cuenta> listar(int limite){
        //limite de negocio
        return crm.listar(Math.min(limite,10_000));
    }
}
