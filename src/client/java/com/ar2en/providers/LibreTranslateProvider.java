package com.ar2en.providers;

import com.ar2en.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Fallback provider: LibreTranslate (https://libretranslate.com) — open source translation.
 * Self-host it with docker for a 100% free, unlimited, key-less setup (see README).
 * Classic MT engine: fast and reliable, but literal — it does not understand gamer slang.
 */
public class LibreTranslateProvider implements TranslationProvider {

        private static final Gson GSON = new Gson();

        private final HttpClient http;

        public LibreTranslateProvider(HttpClient shared) {
                this.http = shared;
        }

        @Override
        public String id() {
                return "libretranslate";
        }

        @Override
        public boolean isConfigured() {
                return ModConfig.get().libreUrl != null && !ModConfig.get().libreUrl.isBlank();
        }

        @Override
        public CompletableFuture<String> translate(String text) {
                ModConfig cfg = ModConfig.get();

                JsonObject body = new JsonObject();
                body.addProperty("q", text);
                body.addProperty("source", "ar");
                body.addProperty("target", "en");
                body.addProperty("format", "text");
                if (cfg.libreApiKey != null && !cfg.libreApiKey.isBlank()) {
                        body.addProperty("api_key", cfg.libreApiKey);
                }

                HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.libreUrl))
                                .header("Content-Type", "application/json")
                                .timeout(Duration.ofSeconds(cfg.timeoutSeconds))
                                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                                .build();

                return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                                .thenApply(response -> {
                                        if (response.statusCode() / 100 != 2) {
                                                throw new CompletionException(new RuntimeException(
                                                                "LibreTranslate HTTP " + response.statusCode()));
                                        }
                                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                                        return json.get("translatedText").getAsString().trim();
                                });
        }
}
