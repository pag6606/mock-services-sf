package com.lab.sfmock;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Path("/services/data/v60.0")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SObjectResource {


    private static final int PAGE_SIZE = 2000;
    // "Base de datos" en memoria, seedeada con datos de prueba
    @Inject
    AccountStore cuentas;

    @GET
    @Path("/sobjects/Account/{id}")
    public Response getAccount(@PathParam("id") String id) {
        SfAccount acc = cuentas.get(id);
        if (acc == null) {
            return Response.status(404)
                    .entity(List.of(Map.of(
                            "message", "The requested resource does not exist",
                            "errorCode", "NOT_FOUND", "fields", List.of())))
                    .build();
        }
        return Response.ok(acc).build();
    }
    @GET
    @Path("/query")
    public QueryResult query(@QueryParam("q") String soql) {
        return ejecutarQuery(soql, 0);
    }

    @GET
    @Path("/query/more")
    public QueryResult queryMore(@QueryParam("q") String soql, @QueryParam("offset") int offset) {
        return ejecutarQuery(soql, offset);
    }

    private QueryResult ejecutarQuery(String soql, int offset) {
        int limiteSolicitado = cuentas.todas().size();
        Matcher m = Pattern.compile("LIMIT\\s+(\\d+)", Pattern.CASE_INSENSITIVE)
                .matcher(soql == null ? "" : soql);
        if (m.find()) limiteSolicitado = Integer.parseInt(m.group(1));

        List<SfAccount> todas = cuentas.todas().stream().toList();
        int totalSize = Math.min(limiteSolicitado, todas.size());

        int finPagina = Math.min(offset + PAGE_SIZE, totalSize);
        List<SfAccount> pagina = offset >= totalSize
                ? List.of()
                : todas.subList(offset, finPagina);

        boolean done = finPagina >= totalSize;
        String nextUrl = done ? null
                : "/services/data/v60.0/query/more?q="
                + java.net.URLEncoder.encode(soql, java.nio.charset.StandardCharsets.UTF_8)
                + "&offset=" + finPagina;

        return new QueryResult(totalSize, done, nextUrl, pagina);
    }

    @POST
    @Path("/sobjects/Case")
    public Response createCase(Map<String, Object> nuevoCaso) {
        String id = "500" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12).toUpperCase();
        // Formato de respuesta real de creación en Salesforce
        return Response.status(201)
                .entity(Map.of("id", id, "success", true, "errors", List.of()))
                .build();
    }

    @PATCH
    @Path("/sobjects/Account/{id}")
    public Response updateAccount(@PathParam("id") String id,
                                  Map<String, Object> cambios) {
        SfAccount actual = cuentas.get(id);
        if (actual == null) return Response.status(404).build();
        if (cambios.containsKey("Estado_Cliente__c")) {
            cuentas.guardar(actual.withEstado((String) cambios.get("Estado_Cliente__c")));
        }
        return Response.status(204).build();  // Salesforce responde 204 en PATCH
    }

    // --- DTOs del mock, con el naming PascalCase real de Salesforce ---
    public record SfAccount(
            @JsonProperty("Id") String id,
            @JsonProperty("Name") String name,
            @JsonProperty("AccountNumber") String accountNumber,
            @JsonProperty("Estado_Cliente__c") String estadoCliente
    ) {
        SfAccount withEstado(String nuevo) {
            return new SfAccount(id, name, accountNumber, nuevo);
        }
    }

    public record QueryResult(int totalSize, boolean done,
                              @JsonProperty("nextRecordsUrl") String nextRecordsUrl,
                              List<SfAccount> records) {}
}
