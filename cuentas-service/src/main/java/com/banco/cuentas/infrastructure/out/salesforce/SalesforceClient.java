package com.banco.cuentas.infrastructure.out.salesforce;

import com.banco.cuentas.infrastructure.out.salesforce.dto.AccountDto;
import com.banco.cuentas.infrastructure.out.salesforce.dto.QueryResultDto;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Map;

@Path("/services/data/v60.0")
@RegisterRestClient(configKey = "salesforce-api")
@RegisterProvider(SalesforceAuthFilter.class)
@RegisterProvider(SalesforceExceptionMapper.class)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SalesforceClient {
    @GET
    @Path("/sobjects/Account/{id}")
    AccountDto getAccount(@PathParam("id") String id);

    @GET
    @Path("/query")
    QueryResultDto query(@QueryParam("q") String soql);

    @GET
    @Path("/query/more")
    QueryResultDto queryMore(@QueryParam("q") String soql, @QueryParam("offset") int offset);

    @PATCH
    @Path("/sobjects/Account/{id}")
    void updateAccount(@PathParam("id") String id, Map<String, Object>  cambios);

}
