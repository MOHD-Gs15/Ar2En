package com.ar2en;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Core hotkey flow:
 * player types Arabic in chat -> presses the hotkey -> we translate asynchronously
 * and swap the text inside the chat input box so the player reviews it and presses
 * Enter themselves. Nothing is ever sent without the player's own Enter key.
 */
public final class ChatTranslator {

        private static final TranslationService SERVICE = new TranslationService();
        private static volatile boolean pending = false;

        private ChatTranslator() {
        }

        public static TranslationService service() {
                return SERVICE;
        }

        public static void onHotkey(Screen screen) {
                if (!(screen instanceof ChatScreen chatScreen)) {
                        return;
                }
                EditBox box = ((com.ar2en.mixin.ChatScreenAccessor) chatScreen).ar2en$input();
                if (box == null) {
                        return;
                }

                String original = box.getValue().trim();
                if (original.isEmpty()) {
                        return; // nothing typed: stay silent, do not spam status
                }
                if (original.startsWith("/")) {
                        status("§7[Ar2En] §fالأوامر التي تبدأ بـ / لا تُترجم");
                        return;
                }
                if (!containsArabic(original)) {
                        status("§7[Ar2En] §fلا يوجد نص عربي في مربع الكتابة");
                        return;
                }
                if (pending) {
                        status("§7[Ar2En] §eالترجمة السابقة ما زالت قيد التنفيذ، انتظر لحظة...");
                        return;
                }

                pending = true;
                status("§7[Ar2En] §e⌛ جارٍ الترجمة...");

                SERVICE.translate(original).whenComplete((result, err) -> {
                        pending = false;
                        Minecraft minecraft = Minecraft.getInstance();
                        minecraft.execute(() -> {
                                if (err != null || result == null || !result.ok()) {
                                        String message = err != null ? rootMessage(err) : result.error();
                                        status("§7[Ar2En] §c✗ فشلت الترجمة: §f" + message);
                                        return;
                                }

                                if (minecraft.screen == chatScreen) {
                                        if (box.getValue().trim().equals(original)) {
                                                // The player has not typed anything new: swap in the translation for review.
                                                box.setValue(result.text());
                                                box.setCursorPosition(result.text().length());
                                                status("§7[Ar2En] §a✓ جاهزة (" + result.provider() + ") — راجع النص ثم اضغط Enter");
                                        } else {
                                                // The player kept typing meanwhile: never overwrite their words.
                                                chat("§7[Ar2En] §6غيّرتَ النص أثناء الترجمة، إليك النتيجة الجاهزة للنسخ: §f" + result.text());
                                        }
                                } else {
                                        chat("§7[Ar2En] §6أغلقتَ الشات قبل اكتمال الترجمة: §f" + result.text());
                                }
                        });
                });
        }

        /** True when the string contains Arabic-script characters (covers standard + extended + presentation forms). */
        public static boolean containsArabic(String s) {
                return s.codePoints().anyMatch(cp ->
                                (cp >= 0x0600 && cp <= 0x06FF)
                                                || (cp >= 0x0750 && cp <= 0x077F)
                                                || (cp >= 0x08A0 && cp <= 0x08FF)
                                                || (cp >= 0xFB50 && cp <= 0xFDFF)
                                                || (cp >= 0xFE70 && cp <= 0xFEFF));
        }

        private static String rootMessage(Throwable t) {
                Throwable cur = t;
                while (cur.getCause() != null && cur.getCause() != cur) {
                        cur = cur.getCause();
                }
                return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
        }

        private static void status(String message) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                        minecraft.player.sendOverlayMessage(Component.literal(message));
                }
        }

        private static void chat(String message) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.gui != null) {
                        minecraft.gui.getChat().addClientSystemMessage(Component.literal(message));
                }
        }
}
