package com.cdek.java.client.auth;

import com.cdek.java.commons.Api;
import com.cdek.java.model.auth.request.AuthRequest;
import com.cdek.java.model.auth.response.AuthResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class CdekAuthServiceImp implements CdekAuthService {

  @Value("${client.id}")
  private String clientId;
  @Value("${client.secret}")
  private String clientSecret;

  private final WebClient webClient;

  public static final String AUTH_HEADER = "Authorization";
  private static final String authUrl = Api.version + "/oauth/token";

  private final static String AUTHORIZATION_KEY = "authorization";
  private final ConcurrentHashMap<String, AuthResponse> authorization = new ConcurrentHashMap<>();

  @Override
  public Mono<AuthResponse> authorize(AuthRequest authRequest) {
    return webClient
        .post()
        .uri(authUrl)
        .attribute("grant_type", authRequest.getGrantType().name().toLowerCase())
        .attribute("client_id", authRequest.getClientId())
        .attribute("client_secret", authRequest.getClientSecret())
        .retrieve()
        .bodyToMono(AuthResponse.class);
  }

  @Override
  public String getFreshJWT() {
    var auth = authorization.get(AUTHORIZATION_KEY);
    var doAuthRequest = false;
    if (auth == null) {
      doAuthRequest = true;
    } else {
      var now = Instant.now();
      var received = auth.getReceived();
      var expiresIn = auth.getExpiresIn();
      if (Duration.between(now, received).toSeconds() > expiresIn) {
        doAuthRequest = true;
      }
    }
    if (doAuthRequest) {
      authorize(new AuthRequest(clientId, clientSecret))
          .doOnNext(authResponse -> authorization.put(AUTHORIZATION_KEY, authResponse))
          .subscribe();
    }
    return "Bearer " + authorization.get(AUTHORIZATION_KEY).getAccessToken();
  }
}