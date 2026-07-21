package com.banco.cuentas.domain.port.out;

import com.banco.cuentas.domain.model.Cuenta;
import com.banco.cuentas.domain.model.EstadoCuenta;

import java.util.List;
import java.util.Optional;

public interface ClienteCrmPort {
    Optional<Cuenta> buscarPorId(String idCrm);
    List<Cuenta> listar(int limite);
    void actualizarEstado(String id, EstadoCuenta estado);
}
