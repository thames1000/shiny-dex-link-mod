package com.thames.shinydexlink.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.thames.shinydexlink.api.dto.ApiResponse;
import com.thames.shinydexlink.api.dto.BerryReportRequest;
import com.thames.shinydexlink.api.dto.BerryResponse;
import com.thames.shinydexlink.api.dto.CatchEventRequest;
import com.thames.shinydexlink.api.dto.LinkRequest;
import com.thames.shinydexlink.api.dto.LinkResponse;
import com.thames.shinydexlink.api.dto.UnlinkRequest;
import com.thames.shinydexlink.config.ShinyDexConfig;
import com.thames.shinydexlink.util.JsonUtil;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public final class ShinyDexApiClient {
    private final ShinyDexConfig config;
    private final Logger logger;
    private final HttpClient httpClient;

    public ShinyDexApiClient(ShinyDexConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.requestTimeoutSeconds))
                .build();
    }

    public CompletableFuture<LinkResponse> verifyLink(LinkRequest request) {
        JsonObject payload = JsonUtil.toObject(request);
        payload.addProperty("serverToken", config.serverToken);
        return post(ApiEndpoints.LINK_VERIFY, payload, LinkResponse.class, LinkResponse.mock("Linked successfully"));
    }

    public CompletableFuture<ApiResponse> unlink(UnlinkRequest request) {
        JsonObject payload = JsonUtil.toObject(request);
        payload.addProperty("serverToken", config.serverToken);
        return post(ApiEndpoints.UNLINK, payload, ApiResponse.class, ApiResponse.success());
    }

    public CompletableFuture<ApiResponse> sendCatch(CatchEventRequest request) {
        JsonObject payload = catchPayload(request);
        return post(ApiEndpoints.CATCHES, payload, ApiResponse.class, ApiResponse.success());
    }

    public CompletableFuture<ApiResponse> sendTestEvent(CatchEventRequest request) {
        JsonObject payload = catchPayload(request);
        return post(ApiEndpoints.TEST_EVENT, payload, ApiResponse.class, ApiResponse.success());
    }

    public CompletableFuture<BerryResponse> sendBerries(BerryReportRequest request) {
        JsonObject payload = JsonUtil.toObject(request);
        payload.addProperty("serverToken", config.serverToken);
        return post(ApiEndpoints.BERRIES, payload, BerryResponse.class, BerryResponse.success());
    }

    public String serializeCatchPayloadForTest(CatchEventRequest request) {
        return JsonUtil.redactedJson(catchPayload(request));
    }

    private JsonObject catchPayload(CatchEventRequest request) {
        JsonObject payload = JsonUtil.toObject(request);
        payload.addProperty("serverToken", config.serverToken);
        return payload;
    }

    private <T> CompletableFuture<T> post(String endpoint, JsonObject payload, Class<T> responseType, T mockResponse) {
        if (config.isMockApi()) {
            logger.info("ShinyDex mock POST {} {}", endpoint, JsonUtil.redactedJson(payload));
            return CompletableFuture.completedFuture(mockResponse);
        }

        URI uri;
        try {
            uri = endpointUri(endpoint);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(config.requestTimeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.GSON.toJson(payload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseResponse(endpoint, response, responseType));
    }

    private URI endpointUri(String endpoint) {
        try {
            URI base = new URI(config.apiBaseUrl.trim());
            if (base.getScheme() == null || base.getHost() == null) {
                throw new URISyntaxException(config.apiBaseUrl, "API base URL must include scheme and host");
            }
            String baseText = base.toString();
            if (baseText.endsWith("/") && endpoint.startsWith("/")) {
                baseText = baseText.substring(0, baseText.length() - 1);
            }
            return new URI(baseText + endpoint);
        } catch (URISyntaxException exception) {
            throw new ApiException("Invalid ShinyDex API URL: " + config.apiBaseUrl, exception);
        }
    }

    private <T> T parseResponse(String endpoint, HttpResponse<String> response, Class<T> responseType) {
        String body = response.body() == null ? "" : response.body();
        if (body.isBlank() && response.statusCode() >= 200 && response.statusCode() < 300) {
            if (responseType == ApiResponse.class) {
                return responseType.cast(ApiResponse.success());
            }
            throw new ApiException("ShinyDex returned an empty response for " + endpoint);
        }

        T parsed;
        try {
            parsed = JsonUtil.GSON.fromJson(body, responseType);
        } catch (JsonParseException exception) {
            throw new ApiException("Malformed JSON response from ShinyDex " + endpoint, exception);
        }

        if (parsed == null) {
            throw new ApiException("Empty JSON response from ShinyDex " + endpoint);
        }

        if ((response.statusCode() < 200 || response.statusCode() >= 300) && !isExplicitFailure(parsed)) {
            String message = extractMessage(parsed);
            throw new ApiException("ShinyDex " + endpoint + " failed with HTTP " + response.statusCode()
                    + (message == null || message.isBlank() ? "" : ": " + message));
        }
        return parsed;
    }

    private boolean isExplicitFailure(Object response) {
        if (response instanceof ApiResponse apiResponse) {
            return !apiResponse.success;
        }
        if (response instanceof LinkResponse linkResponse) {
            return !linkResponse.success;
        }
        if (response instanceof BerryResponse berryResponse) {
            return !berryResponse.success;
        }
        return false;
    }

    private String extractMessage(Object response) {
        if (response instanceof ApiResponse apiResponse) {
            return apiResponse.message;
        }
        if (response instanceof LinkResponse linkResponse) {
            return linkResponse.message;
        }
        if (response instanceof BerryResponse berryResponse) {
            return berryResponse.message;
        }
        return null;
    }
}
