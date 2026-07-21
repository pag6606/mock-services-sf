package com.banco.cuentas.infrastructure.out.salesforce;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path( "/services/oauth2")
@RegisterRestClient(configKey = "salesforce-auth")
public interface SalesforceAuthClient {

    @POST
    @Path("/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    TokenResponse getToken(@FormParam("grant_type") String grantType,
                           @FormParam("assertion") String assertion);

    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("instance_url") String instanceUrl,
                         @JsonProperty("token_type") String tokenType){}
}
