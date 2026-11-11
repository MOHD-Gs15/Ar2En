package com.ar2en;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON configuration stored at config/ar2en.json.
 * Missing fields are automatically filled with defaults on load.
 *
 * "Live" settings (groqModel, providers, customPrompt) are what the translators
 * actually use. Profiles are named snapshots of those settings; switching a
 * profile copies its values onto the live fields.
 */
public class ModConfig {

        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("ar2en.json");

        /** A named snapshot of settings (model + provider chain + system prompt). */
        public static class Profile {
                public String name = "";
                public String model = "openai/gpt-oss-120b";
                public List<String> providers = new ArrayList<>(List.of("groq", "libretranslate"));
                /** Empty string = use the built-in gamer-slang prompt. */
                public String prompt = "";

                public Profile() {
                }

                public Profile(String name, String model, List<String> providers, String prompt) {
                        this.name = name;
                        this.model = model;
                        this.providers = new ArrayList<>(providers);
                        this.prompt = prompt == null ? "" : prompt;
                }
        }

        /** Master switch for the whole mod (hotkey + status). */
        public boolean enabled = true;

        /** Translation providers, tried in this order until one succeeds. Values: groq | libretranslate | google */
        public List<String> providers = new ArrayList<>(List.of("groq", "libretranslate"));

        /** Groq API key (free from https://console.groq.com). Empty = groq disabled. */
        public String groqApiKey = "";

        /** Currently selected Groq model id. */
        public String groqModel = "openai/gpt-oss-120b";

        /** Presets used by /ar2en model quality|fast. */
        public String groqModelQuality = "openai/gpt-oss-120b";
        public String groqModelFast = "openai/gpt-oss-20b";

        /** Custom system prompt sent to the AI. Empty = built-in gamer slang prompt. */
        public String customPrompt = "";

        /** Saved profiles (named snapshots). activeProfile points into this list, -1 = none. */
        public List<Profile> profiles = new ArrayList<>();
        public int activeProfile = -1;

        /** Model ids known to the GUI (refreshed live from Groq via the fetch button). */
        public List<String> knownModels = new ArrayList<>(List.of("openai/gpt-oss-120b", "openai/gpt-oss-20b"));

        /**
         * Canonical name of the translate hotkey, e.g. "key.keyboard.right.shift".
         * Names are stable across Minecraft versions, unlike the raw key codes, which
         * changed completely in 26.3 (GLFW -> SDL). Null until {@link #normalize} resolves it.
         */
        public String hotkeyKey = null;

        /**
         * Numeric key code of the translate hotkey.
         * On Minecraft 26.1.x this was a GLFW code (the default was
         * {@link Hotkeys#LEGACY_DEFAULT_CODE}); since 26.3 it is an SDL code.
         * Kept in the file so older configs can be migrated on the first 26.3 launch.
         */
        public int hotkeyCode = InputConstants.KEY_RSHIFT;

        /** LibreTranslate endpoint (POST). Point it at your own docker instance for unlimited free use. */
        public String libreUrl = "https://libretranslate.com/translate";
        public String libreApiKey = "";

        /** Per-request HTTP timeout in seconds. */
        public int timeoutSeconds = 10;

        /** Max number of cached translations kept in memory. */
        public int cacheSize = 500;

        private static ModConfig instance;

        public static ModConfig get() {
                if (instance == null) {
                        load();
                }
                return instance;
        }

        public static void load() {
                ModConfig fresh = new ModConfig();
                if (Files.exists(PATH)) {
                        try {
                                ModConfig read = GSON.fromJson(Files.readString(PATH), ModConfig.class);
                                if (read != null) {
                                        fresh = read;
                                        if (fresh.providers == null) fresh.providers = new ArrayList<>(List.of("groq", "libretranslate"));
                                }
                        } catch (Exception e) {
                                Ar2EnClient.LOGGER.error("[Ar2En] Could not parse ar2en.json, using defaults", e);
                        }
                }
                normalize(fresh);
                instance = fresh;
                save();
        }

        /** Guarantees invariants: non-null lists, at least one profile, activeProfile in bounds. */
        private static void normalize(ModConfig cfg) {
                if (cfg.profiles == null) cfg.profiles = new ArrayList<>();
                if (cfg.knownModels == null) cfg.knownModels = new ArrayList<>(List.of("openai/gpt-oss-120b", "openai/gpt-oss-20b"));
                if (cfg.customPrompt == null) cfg.customPrompt = "";
                normalizeHotkey(cfg);
                if (cfg.profiles.isEmpty()) {
                        // Migration / first run: wrap the current live settings as the default profile.
                        cfg.profiles.add(new Profile("القازرز", cfg.groqModel, cfg.providers, cfg.customPrompt));
                        cfg.activeProfile = 0;
                }
                if (cfg.activeProfile < 0 || cfg.activeProfile >= cfg.profiles.size()) {
                        cfg.activeProfile = 0;
                }
                // The selected model is always selectable in the GUI, even before a fetch.
                if (!cfg.knownModels.contains(cfg.groqModel)) {
                        cfg.knownModels.add(0, cfg.groqModel);
                }
        }

        /**
         * Resolves the translate hotkey.
         *
         * <p>Minecraft 26.3 replaced GLFW with SDL and every key code changed value
         * (Right Shift went from 344 to 229). Configs written by Ar2En &lt;= 1.1.0 only
         * contain the old GLFW code, so it is translated to the canonical key name
         * ("key.keyboard.right.shift"), which is identical on both versions.
         */
        private static void normalizeHotkey(ModConfig cfg) {
                if (cfg.hotkeyKey == null || cfg.hotkeyKey.isBlank()) {
                        String migrated = Hotkeys.nameForLegacyCode(cfg.hotkeyCode);
                        cfg.hotkeyKey = (migrated != null) ? migrated : Hotkeys.DEFAULT_NAME;
                }
                InputConstants.Key resolved = Hotkeys.resolve(cfg.hotkeyKey);
                if (resolved == null) {
                        // Unusable name -> fall back to the default binding.
                        cfg.hotkeyKey = Hotkeys.DEFAULT_NAME;
                        resolved = Hotkeys.defaultKey();
                }
                cfg.hotkeyCode = Hotkeys.codeOf(resolved);
        }

        // ---------------- profile helpers ----------------

        /** Copies the given profile's values onto the live settings. */
        public void applyProfile(int index) {
                if (index < 0 || index >= profiles.size()) {
                        return;
                }
                Profile p = profiles.get(index);
                activeProfile = index;
                groqModel = p.model;
                providers = new ArrayList<>(p.providers);
                customPrompt = p.prompt == null ? "" : p.prompt;
        }

        /** Writes the current live settings back into the active profile (keeps the snapshot in sync). */
        public void syncActiveProfile() {
                if (activeProfile < 0 || activeProfile >= profiles.size()) {
                        return;
                }
                Profile p = profiles.get(activeProfile);
                p.model = groqModel;
                p.providers = new ArrayList<>(providers);
                p.prompt = customPrompt;
        }

        /** Creates a new profile named after the current live settings and activates it. */
        public void addProfile(String name) {
                String safe = name == null || name.isBlank() ? "بروفايل " + (profiles.size() + 1) : name.trim();
                profiles.add(new Profile(safe, groqModel, providers, customPrompt));
                activeProfile = profiles.size() - 1;
        }

        /** Deletes the active profile; if it was the last one a fresh default is created from live settings. */
        public void deleteActiveProfile() {
                if (activeProfile >= 0 && activeProfile < profiles.size()) {
                        profiles.remove(activeProfile);
                }
                if (profiles.isEmpty()) {
                        profiles.add(new Profile("القازرز", groqModel, providers, customPrompt));
                }
                activeProfile = Math.min(activeProfile, profiles.size() - 1);
                if (activeProfile < 0) {
                        activeProfile = 0;
                }
                applyProfile(activeProfile);
        }

        public static void save() {
                try {
                        Files.createDirectories(PATH.getParent());
                        Files.writeString(PATH, GSON.toJson(instance));
                } catch (IOException e) {
                        Ar2EnClient.LOGGER.error("[Ar2En] Could not save ar2en.json", e);
                }
        }

        public static Path path() {
                return PATH;
        }
}
