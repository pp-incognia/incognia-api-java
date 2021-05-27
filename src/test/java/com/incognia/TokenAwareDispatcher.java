package com.incognia;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;

public class TokenAwareDispatcher extends Dispatcher {
  private final String token;
  private final String clientId;
  private final String clientSecret;
  private final ObjectMapper objectMapper;
  @Setter private String expectedInstallationId;
  @Setter private String expectedAddressLine;
  @Getter private int tokenRequestCount;

  public TokenAwareDispatcher(String token, String clientId, String clientSecret) {
    this.token = token;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.tokenRequestCount = 0;
    this.objectMapper = ObjectMapperFactory.OBJECT_MAPPER;
  }

  @SneakyThrows
  @NotNull
  @Override
  public MockResponse dispatch(@NotNull RecordedRequest request) {
    if ("/api/v2/onboarding".equals(request.getPath()) && "POST".equals(request.getMethod())) {
      assertThat(request.getHeader("Content-Type")).contains("application/json");
      assertThat(request.getHeader("Authorization")).isEqualTo("Bearer " + token);
      return new MockResponse().setResponseCode(200).setBody("{\"name\": \"my awesome name\"}");
    }
    if ("/api/v2/onboarding/signups".equals(request.getPath())
        && "POST".equals(request.getMethod())) {
      assertThat(request.getHeader("Content-Type")).contains("application/json");
      assertThat(request.getHeader("Authorization")).isEqualTo("Bearer " + token);
      PostSignupRequestBody postSignupRequestBody =
          objectMapper.readValue(request.getBody().inputStream(), PostSignupRequestBody.class);
      assertThat(postSignupRequestBody.getInstallationId()).isEqualTo(expectedInstallationId);
      assertThat(postSignupRequestBody.getAddressLine()).isEqualTo(expectedAddressLine);
      String response = getResourceFileAsString("post_onboarding_response.json");
      return new MockResponse().setResponseCode(200).setBody(response);
    }
    if ("/api/v1/token".equals(request.getPath()) && "POST".equals(request.getMethod())) {
      return handleTokenRequest(request);
    }
    return new MockResponse().setResponseCode(404);
  }

  @NotNull
  private MockResponse handleTokenRequest(@NotNull RecordedRequest request) {
    tokenRequestCount++;
    String authorizationHeader = request.getHeader("Authorization");
    assertThat(authorizationHeader).startsWith("Basic");
    assertThat(request.getHeader("Content-Type")).contains("application/x-www-form-urlencoded");
    String[] idAndSecret =
        new String(
                Base64.getUrlDecoder().decode(authorizationHeader.split(" ")[1]),
                StandardCharsets.UTF_8)
            .split(":", 2);
    String clientId = idAndSecret[0];
    String clientSecret = idAndSecret[1];
    if (this.clientId.equals(clientId) && this.clientSecret.equals(clientSecret)) {
      return new MockResponse()
          .setResponseCode(200)
          .setBody(
              "{\"access_token\": \""
                  + token
                  + "\",\"expires_in\": 100,\"token_type\": \"Bearer\"}");
    }
    return new MockResponse().setResponseCode(401);
  }

  @SneakyThrows
  static String getResourceFileAsString(String fileName) {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    try (InputStream is = classLoader.getResourceAsStream(fileName)) {
      if (is == null) return null;
      try (InputStreamReader isr = new InputStreamReader(is);
          BufferedReader reader = new BufferedReader(isr)) {
        return reader.lines().collect(Collectors.joining(System.lineSeparator()));
      }
    }
  }
}
