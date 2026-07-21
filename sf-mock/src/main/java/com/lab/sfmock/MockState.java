package com.lab.sfmock;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class MockState {
  public enum ChaosMode {
    OK, ERROR_500, TIMEOUT, RATE_LIMIT
  }

  private final Set<String> tokensValidos = ConcurrentHashMap.newKeySet();
  private volatile ChaosMode chaos = ChaosMode.OK;

  public String emitirToken() {
    String token = "Mock-" + java.util.UUID.randomUUID();
    tokensValidos.add(token);
    return token;
  }

  public boolean isTokenValido(String token) {
    return tokensValidos.contains(token);
  }

  public void revocarTodos() {
    tokensValidos.clear();
  }

  public ChaosMode getChaos() {
    return chaos;
  }

  public void setChaos(ChaosMode chaos) {
    this.chaos = chaos;
  }
}
