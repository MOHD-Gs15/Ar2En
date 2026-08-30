package com.ar2en.providers;

import com.ar2en.ModConfig;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Last-resort provider: Google Translate's public web endpoint (no key required).
 * Disabled by default; enable with /ar2en provider google or from the GUI.
 */
public class GoogleProvider implements TranslationProvider {

        private final HttpClient http;

        public GoogleProvider(HttpClient shared) {
                this.http = shared;
        }

        @Override
        public String id() {
                return "google";
        }

        @Override
        public boolean isConfigured() {
                return true;
        }

        @Override
        public CompletableFuture<String> translate(String text) {
                ModConfig cfg = ModConfig.get();
                String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=ar&tl=en&dt=t&q="
                                + URLEncoder.encode(text, StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(url))
                                .timeout(Duration.ofSeconds(cfg.timeoutSeconds))
                                .GET()
                                .build();

                return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                                .thenApply(response -> {
                                        if (response.statusCode() / 100 != 2) {
                                                throw new CompletionException(new RuntimeException(
                                                                "Google HTTP " + response.statusCode()));
                                        }
                                        // Response shape: [[["translated","original",null,null,...],...],...]
                                        StringBuilder sb = new StringBuilder();
                                        for (var segment : JsonParser.parseString(response.body()).getAsJsonArray().get(0).getAsJsonArray()) {
                                                sb.append(segment.getAsJsonArray().get(0).getAsString());
                                        }
                                        return sb.toString().trim();
                                });
        }
}
