package com.lab.sfmock;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lab.sfmock.MockState;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/services/oauth2")
public class TokenResource {

  private final String JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer";


  @Inject
  MockState state;

  @POST
  @Path("/token")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  public Response token(@FormParam("grant_type") String grantType, @FormParam("assertion") String assertion){
    if(!JWT_BEARER.equals(grantType)){
      return Response.status(400)
      .entity(Map.of("error", "unsupported_grant_type", "error_description", "The grant type is not supported"))
      .build();
    }
    // Validación superficial del JWT: 3 partes separadas por punto.
    // (Salesforce real verifica la FIRMA contra el certificado de la Connected App)
    if (assertion == null || assertion.split("\\.").length != 3) {
      return Response.status(400)
      .entity(Map.of("error", "invalid_request", "error_description", "The assertion is not a valid JWT"))
      .build();
    }

    String accessToken= state.emitirToken();
    return Response.ok(new TokenResponse(accessToken, "http://localhost:8081", "Bearer")).build();


  } 

  public record TokenResponse(@JsonProperty("access_token") String accessToken,
    @JsonProperty("instance_url") String instanceUrl,
    @JsonProperty("token_type") String tokenType) {}

}
