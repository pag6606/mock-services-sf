package com.banco.cuentas.infrastructure.out.salesforce;

import io.quarkus.logging.Log;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class SalesforceTokenService {

    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    private final SalesforceAuthClient authClient;
    private final SalesfoceConfig config;

    private final AtomicReference<CachedToken> cached = new AtomicReference<>();

    @Inject
    public SalesforceTokenService(
            @RestClient SalesforceAuthClient authClient,
            SalesfoceConfig config) {
        this.authClient = authClient;
        this.config = config;
    }

    public String getAccessToken() {
        CachedToken c = cached.get();
        if (c != null && c.isValid()) return c.token();
        return refresh();
    }

    private synchronized String refresh() {
        CachedToken current = cached.get();
        if (current != null && current.isValid()) return current.token();

        String assertion = Jwt.claims()
                .issuer(config.auth().clientId())
                .subject(config.auth().username())
                .audience(config.auth().audience())
                .expiresIn(Duration.ofMinutes(3))
                .sign();

        var resp = authClient.getToken(GRANT_TYPE, assertion);

        CachedToken nuevo = new CachedToken(resp.accessToken(),
                Instant.now().plus(Duration.ofMinutes(config.tokenTtlMinutes())));
        cached.set(nuevo);
        Log.info("Token Salesforce renovado");
        return nuevo.token();
    }

    public void invalidate() {
        cached.set(null);
    }

    private record CachedToken(String token, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt.minusSeconds(60));
        }
    }
}