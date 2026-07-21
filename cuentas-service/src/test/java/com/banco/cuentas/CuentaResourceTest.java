package com.banco.cuentas;

import com.banco.cuentas.infrastructure.out.salesforce.SalesforceTokenService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.banco.cuentas.WireMockSalesforce.server;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@QuarkusTestResource(WireMockSalesforce.class)
class CuentaResourceTest {

    @Inject
    SalesforceTokenService tokenService;

    @BeforeEach
    void reset() {
        server.resetRequests();
        server.resetScenarios();
        tokenService.invalidate();   // evita que el token cacheado entre tests altere el conteo
    }

    @Test
    void cuentaExistente_devuelve200ConContratoPropio() {
        server.stubFor(get(urlEqualTo("/services/data/v60.0/sobjects/Account/001AAA"))
                .withHeader("Authorization", equalTo("Bearer TEST-TOKEN"))
                .willReturn(okJson("""
                {"Id":"001AAA","Name":"Test","AccountNumber":"ACC-1",
                 "Estado_Cliente__c":"ACTIVO"}
                """)));

        given().when().get("/cuentas/001AAA")
                .then().statusCode(200)
                .body("nombre", is("Test"))
                .body("operativa", is(true));
    }

    @Test
    void cuentaInexistente_devuelve404DeNegocio() {
        server.stubFor(get(urlPathMatching("/services/data/.*/Account/NOPE"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                    [{"message":"not found","errorCode":"NOT_FOUND","fields":[]}]
                    """)));

        given().when().get("/cuentas/NOPE")
                .then().statusCode(404)
                .body("error", is("CUENTA_NO_ENCONTRADA"));
    }

    @Test
    void error401_renuevaTokenYReintenta() {
        server.stubFor(get(urlPathMatching("/services/data/.*/Account/001AAA"))
                .inScenario("sesion").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                    [{"message":"expired","errorCode":"INVALID_SESSION_ID","fields":[]}]
                    """))
                .willSetStateTo("renovado"));

        server.stubFor(get(urlPathMatching("/services/data/.*/Account/001AAA"))
                .inScenario("sesion").whenScenarioStateIs("renovado")
                .willReturn(okJson("""
                {"Id":"001AAA","Name":"PostRenovacion","AccountNumber":"ACC-1",
                 "Estado_Cliente__c":"ACTIVO"}
                """)));

        given().when().get("/cuentas/001AAA")
                .then().statusCode(200)
                .body("nombre", is("PostRenovacion"));

        server.verify(moreThanOrExactly(2),
                postRequestedFor(urlEqualTo("/services/oauth2/token")));
    }
}