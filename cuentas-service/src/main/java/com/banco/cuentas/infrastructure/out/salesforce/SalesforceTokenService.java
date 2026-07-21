package com.banco.cuentas.infrastructure.out.salesforce;

import io.quarkus.logging.Log;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class SalesforceTokenService {

    private static final String GRANT_TYPE="urn:ietf:params:oauth:grant-type:jwt-bearer";

    @Inject
    @RestClient
    SalesforceAuthClient authClient;

    @Inject
    SalesfoceConfig config;

    private volatile CachedToken cached;

    public String getAccessToken(){
        CachedToken c= cached;
        if (c!= null && c.isValid()) return c.token();
        return refresh();
    }
    private synchronized String refresh() {
        if (cached != null && cached.isValid()) return cached.token();

        String assertion = Jwt.claims()
                .issuer(config.auth().clientId())
                .subject(config.auth().username())
                .audience(config.auth().audience())
                .expiresIn(Duration.ofMinutes(3))   // máximo que acepta Salesforce
                .sign();                            // usa smallrye.jwt.sign.key.location

        var resp = authClient.getToken(GRANT_TYPE, assertion);

        // JWT Bearer NO devuelve expires_in → TTL propio conservador
        cached = new CachedToken(resp.accessToken(),
                Instant.now().plus(Duration.ofMinutes(config.tokenTtlMinutes())));
        Log.info("Token Salesforce renovado");
        return cached.token();
    }

    /** Invocar ante un 401: fuerza renovación en la siguiente llamada */
    public void invalidate() { cached = null; }

    private record CachedToken(String token, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt.minusSeconds(60));  // margen 60s
        }
    }

}
