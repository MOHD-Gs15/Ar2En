package com.ar2en.providers;

import java.util.concurrent.CompletableFuture;

/** One translation backend. Implementations must be thread-safe and never block the calling thread. */
public interface TranslationProvider {

        /** Short id used in logs and chat feedback (groq, libretranslate, google). */
        String id();

        /** False = this provider is skipped (missing credentials, blank endpoint, ...). */
        boolean isConfigured();

        /** Translates Arabic text to English asynchronously; failures must surface as exceptional futures. */
        CompletableFuture<String> translate(String text);
}
