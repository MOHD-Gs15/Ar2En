package com.ar2en;

import com.ar2en.providers.GoogleProvider;
import com.ar2en.providers.GroqProvider;
import com.ar2en.providers.LibreTranslateProvider;
import com.ar2en.providers.TranslationProvider;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Tries the configured providers in order (first success wins) and keeps a small LRU cache
 * so repeated messages never hit the network twice.
 */
public class TranslationService {

        private final HttpClient http = HttpClient.newBuilder()
                        .executor(Executors.newVirtualThreadPerTaskExecutor())
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

        private final Map<String, String> cache;

        public record Result(String text, String provider, String error, boolean ok) {
                static Result ok(String text, String provider) {
                        return new Result(text, provider, null, true);
                }

                static Result fail(String error) {
                        return new Result(null, null, error, false);
                }
        }

        public TranslationService() {
                int size = Math.max(50, ModConfig.get().cacheSize);
                this.cache = new LinkedHashMap<>(16, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                                return size() > size;
                        }
                };
        }

        public HttpClient http() {
                return http;
        }

        /** Drops every cached translation (call after changing model, prompt or provider). */
        public void clearCache() {
                synchronized (cache) {
                        cache.clear();
                }
        }

        /** Builds the provider chain from config; silently skips providers that are disabled or missing credentials. */
        public List<TranslationProvider> buildChain() {
                List<TranslationProvider> chain = new ArrayList<>();
                for (String name : ModConfig.get().providers) {
                        TranslationProvider provider = switch (name.toLowerCase()) {
                                case "groq" -> new GroqProvider(http);
                                case "libretranslate" -> new LibreTranslateProvider(http);
                                case "google" -> new GoogleProvider(http);
                                default -> null;
                        };
                        if (provider != null && provider.isConfigured()) {
                                chain.add(provider);
                        }
                }
                return chain;
        }

        public CompletableFuture<Result> translate(String text) {
                String cached;
                synchronized (cache) {
                        cached = cache.get(text);
                }
                if (cached != null) {
                        return CompletableFuture.completedFuture(Result.ok(cached, "cache"));
                }

                List<TranslationProvider> chain = buildChain();
                if (chain.isEmpty()) {
                        return CompletableFuture.completedFuture(Result.fail(
                                        "لا يوجد مزود ترجمة جاهز — أضف مفتاح Groq عبر /ar2en key أو فعّل google/libretranslate عبر /ar2en provider"));
                }
                return tryChain(chain, 0, text, new ArrayList<>());
        }

        private CompletableFuture<Result> tryChain(List<TranslationProvider> chain, int index, String text, List<String> errors) {
                if (index >= chain.size()) {
                        return CompletableFuture.completedFuture(Result.fail(String.join(" | ", errors)));
                }
                TranslationProvider provider = chain.get(index);
                return provider.translate(text)
                                .thenApply(output -> {
                                        if (output == null || output.isBlank()) {
                                                throw new IllegalStateException(provider.id() + ": رد فارغ");
                                        }
                                        synchronized (cache) {
                                                cache.put(text, output);
                                        }
                                        return Result.ok(output, provider.id());
                                })
                                .exceptionallyCompose(err -> {
                                        Throwable cause = err.getCause() != null ? err.getCause() : err;
                                        errors.add(provider.id() + ": " + cause.getMessage());
                                        return tryChain(chain, index + 1, text, errors);
                                });
        }
}
