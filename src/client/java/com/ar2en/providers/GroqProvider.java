package com.ar2en.providers;

import com.ar2en.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
 * Primary provider: GroqCloud (https://groq.com) — free API key, extremely fast LPU inference.
 * Uses an LLM instead of a classic translator so gaming slang, abbreviations and the
 * emotional tone of the message survive the translation.
 */
public class GroqProvider implements TranslationProvider {

        public static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
        public static final String MODELS_URL = "https://api.groq.com/openai/v1/models";

        private static final Gson GSON = new Gson();

        private final HttpClient http;

        public GroqProvider(HttpClient shared) {
                this.http = shared;
        }

        @Override
        public String id() {
                return "groq";
        }

        @Override
        public boolean isConfigured() {
                return ModConfig.get().groqApiKey != null && !ModConfig.get().groqApiKey.isBlank();
        }

        /** The user's custom prompt when set, otherwise the built-in gamer slang prompt. */
        public static String effectivePrompt(ModConfig cfg) {
                if (cfg.customPrompt != null && !cfg.customPrompt.isBlank()) {
                        return cfg.customPrompt.trim();
                }
                return systemPrompt();
        }

        /**
         * The brain of the mod: instructs the model to output real gamer English
         * (lowercase, abbreviations, matching mood) instead of stiff textbook translation.
         */
        public static String systemPrompt() {
                return """
                                You translate casual Arabic Minecraft multiplayer chat messages into English that sounds like a real native gamer typing in chat.

                                Hard rules:
                                - Output ONLY the translated message. No quotes, no explanations, no prefix, no suffix.
                                - Style: relaxed gaming slang with common abbreviations (u, ur, rn, im, wyd, hbu, afk, brb, gg, lol, lmao, dang, nah, yeah, gonna, kinda, smh, tbh).
                                - Keep the emotional tone: hype stays hype, angry stays angry, sarcastic stays sarcastic, sad stays sad.
                                - Keep it SHORT like chat. Never produce formal or full sentences unless the original is formal.
                                - Minecraft vocabulary stays exactly as native players use it (creeper, netherite, elytra, respawn, xp, mob, biome...).
                                - Latin slang or abbreviations already inside the Arabic message (wyd, afk, gg, lol...) must survive untouched.
                                - If the message is already mostly English, return it unchanged.
                                - Never censor and never soften. Mild swearing stays at the same mild level (e.g. dang, damn, wth).
                                - Preserve emojis and kaomoji if present.
                                - Numbers, player names and server names stay unchanged.""";
        }

        @Override
        public CompletableFuture<String> translate(String text) {
                ModConfig cfg = ModConfig.get();

                JsonObject body = new JsonObject();
                body.addProperty("model", cfg.groqModel);
                body.addProperty("temperature", 0.4);
                body.addProperty("max_tokens", 300);
                if (cfg.groqModel != null && cfg.groqModel.contains("gpt-oss")) {
                        // gpt-oss reasoning models: lowest reasoning effort for chat-speed latency.
                        body.addProperty("reasoning_effort", "low");
                }

                JsonArray messages = new JsonArray();
                JsonObject sys = new JsonObject();
                sys.addProperty("role", "system");
                sys.addProperty("content", effectivePrompt(cfg));
                JsonObject user = new JsonObject();
                user.addProperty("role", "user");
                user.addProperty("content", text);
                messages.add(sys);
                messages.add(user);
                body.add("messages", messages);

                HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                                .header("Content-Type", "application/json")
                                .header("Authorization", "Bearer " + cfg.groqApiKey)
                                .timeout(Duration.ofSeconds(cfg.timeoutSeconds))
                                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                                .build();

                return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                                .thenApply(response -> {
                                        int status = response.statusCode();
                                        if (status == 401) {
                                                throw new CompletionException(new RuntimeException("Groq: مفتاح API غير صالح (401) — تحقق من /ar2en key"));
                                        }
                                        if (status == 429) {
                                                throw new CompletionException(new RuntimeException("Groq: تجاوزت حد الاستخدام المجاني مؤقتاً (429) — انتظر قليلاً"));
                                        }
                                        if (status == 404 || (status == 400 && response.body().contains("model"))) {
                                                throw new CompletionException(new RuntimeException("Groq: النموذج " + cfg.groqModel + " غير متوفر — جرّب /ar2en models لعرض المتاح"));
                                        }
                                        if (status / 100 != 2) {
                                                throw new CompletionException(new RuntimeException("Groq HTTP " + status + ": " + shorten(response.body())));
                                        }
                                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                                        String content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                                                        .getAsJsonObject("message")
                                                        .get("content").getAsString();
                                        return clean(content);
                                });
        }

        /** Fetches the live list of model ids available to this key (Groq rotates models over time). */
        public CompletableFuture<String> listModels() {
                ModConfig cfg = ModConfig.get();
                HttpRequest request = HttpRequest.newBuilder(URI.create(MODELS_URL))
                                .header("Authorization", "Bearer " + cfg.groqApiKey)
                                .timeout(Duration.ofSeconds(cfg.timeoutSeconds))
                                .GET()
                                .build();

                return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                                .thenApply(response -> {
                                        if (response.statusCode() != 200) {
                                                throw new CompletionException(new RuntimeException("HTTP " + response.statusCode()));
                                        }
                                        JsonArray data = JsonParser.parseString(response.body()).getAsJsonObject()
                                                        .getAsJsonArray("data");
                                        StringBuilder sb = new StringBuilder();
                                        for (var element : data) {
                                                sb.append(element.getAsJsonObject().get("id").getAsString()).append('\n');
                                        }
                                        return sb.toString().trim();
                                });
        }

        static String clean(String s) {
                String out = s.trim();
                if (out.length() >= 2 && out.startsWith("\"") && out.endsWith("\"")) {
                        out = out.substring(1, out.length() - 1).trim();
                }
                return out.replace("\n", " ").replaceAll("\\s+", " ").trim();
        }

        private static String shorten(String s) {
                return s.length() > 140 ? s.substring(0, 140) + "…" : s;
        }
}
