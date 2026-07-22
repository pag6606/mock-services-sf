package com.lab.sfmock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AccountStore {

    private final Map<String, SObjectResource.SfAccount> cuentas = new ConcurrentHashMap<>();

    @Inject
    ObjectMapper mapper;

    void cargar(@Observes StartupEvent ev){
        try(InputStream is = getClass().getResourceAsStream("/accounts.json")){
            if(is==null){
                throw new IllegalStateException("accounts.json no encontrado en resources");
            }
            List<SObjectResource.SfAccount> lista=
                    mapper.readValue(is, new TypeReference<>() {
                    });
            lista.forEach(cuenta->cuentas.put(cuenta.id(), cuenta));
            Log.infof("AccountStore: %d cuentas cargadas desde accounts.json", cuentas.size());
        } catch (Exception e) {
            throw new RuntimeException("Fallo cargando accounts.json", e);
        }
    }

    public SObjectResource.SfAccount get(String id) { return cuentas.get(id); }
    public java.util.Collection<SObjectResource.SfAccount> todas() { return cuentas.values(); }
    public void guardar(SObjectResource.SfAccount a) { cuentas.put(a.id(), a); }
}



// trigger de prueba mar 21 jul 2026 10:07:12 -05
// touch 1784740906
