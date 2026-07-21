package com.banco.cuentas;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;


public class WireMockSalesforce implements QuarkusTestResourceLifecycleManager {

    public static WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(0);
        server.start();

        // Token siempre disponible para cualquier test
        server.stubFor(post(urlEqualTo("/services/oauth2/token"))
                .willReturn(okJson("""
                {"access_token":"TEST-TOKEN","instance_url":"http://t","token_type":"Bearer"}
                """)));

        return Map.of(
                "quarkus.rest-client.salesforce-api.url", server.baseUrl(),
                "quarkus.rest-client.salesforce-auth.url", server.baseUrl()
        );
    }

    @Override
    public void stop() {
        if (server != null) server.stop();
    }
}
