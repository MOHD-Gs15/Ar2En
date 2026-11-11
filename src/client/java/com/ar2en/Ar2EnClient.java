package com.ar2en;

import com.ar2en.providers.GroqProvider;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

public class Ar2EnClient implements ClientModInitializer {

        public static final String MOD_ID = "ar2en";
        public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

        private static KeyMapping translateKey;

        /** The rebinding GUI needs access to the registered mapping. */
        public static KeyMapping translateKey() {
                return translateKey;
        }

        @Override
        public void onInitializeClient() {
                ModConfig cfgLoaded = ModConfig.get();

                // Minecraft 26.3 replaced GLFW with SDL: key codes now come from
                // InputConstants.KEY_* and the key type is Type.KEYBOARD.
                translateKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                                "key.ar2en.translate",
                                InputConstants.KEY_RSHIFT,
                                KeyMapping.Category.MISC
                ));

                // The GUI lets the player rebind the hotkey; the binding is persisted in
                // ar2en.json (options.txt would not persist runtime setKey calls), so
                // re-apply the saved key on every launch. The binding is stored by its
                // canonical name, so it survives the GLFW -> SDL key code change.
                InputConstants.Key savedKey = Hotkeys.resolve(cfgLoaded.hotkeyKey);
                if (savedKey != null) {
                        translateKey.setKey(savedKey);
                }

                // Chat screens swallow keyboard input, so regular keybinding polling never
                // fires while typing. Instead we hook the chat screen's keyboard events
                // through the Fabric screen API and trigger on our (rebindable) key.
                ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
                        if (screen instanceof ChatScreen) {
                                ScreenKeyboardEvents.allowKeyPress(screen).register((scr, keyEvent) -> {
                                        boolean ours = ModConfig.get().enabled && translateKey != null && translateKey.matches(keyEvent);
                                        if (ours) {
                                                ChatTranslator.onHotkey(scr);
                                                return false; // consume the key so it is not typed into the field
                                        }
                                        return true; // let every other key reach the chat field normally
                                });
                        }
                });

                registerCommands();

                LOGGER.info("[Ar2En] loaded — type Arabic in chat and press Right Shift to translate it.");
        }

        private void registerCommands() {
                        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                                ClientCommands.literal("ar2en")
                                                .executes(ctx -> {
                                                        ModConfig cfg = ModConfig.get();
                                                        feedback("§7[Ar2En] §fالحالة: " + (cfg.enabled ? "§aمفعّل" : "§cمعطّل")
                                                                        + " §7| المزودون: §f" + String.join(", ", cfg.providers)
                                                                        + " §7| النموذج: §f" + cfg.groqModel
                                                                        + " §7| المفتاح: " + (cfg.groqApiKey.isBlank() ? "§cغير موجود" : "§aموجود"));
                                                        feedback("§7الاستخدام: افتح الشات T، اكتب بالعربي، اضغط Right Shift، راجع، ثم Enter");
                                                        return 1;
                                                })
                                                .then(ClientCommands.literal("key")
                                                                .then(ClientCommands.argument("apikey", StringArgumentType.greedyString())
                                                                                .executes(ctx -> {
                                                                                        String key = StringArgumentType.getString(ctx, "apikey").trim();
                                                                                        if (key.isEmpty()) {
                                                                                                ctx.getSource().sendError(Component.literal("§7[Ar2En] §cالمفتاح فارغ"));
                                                                                                return 0;
                                                                                        }
                                                                                        ModConfig.get().groqApiKey = key;
                                                                                        ModConfig.save();
                                                                                        feedback("§7[Ar2En] §aتم حفظ مفتاح Groq في " + ModConfig.path().getFileName()
                                                                                                        + " §7(محلي على جهازك فقط)");
                                                                                        return 1;
                                                                                })))
                                                .then(ClientCommands.literal("model")
                                                                .then(ClientCommands.argument("model", StringArgumentType.greedyString())
                                                                                .executes(ctx -> {
                                                                                        String value = StringArgumentType.getString(ctx, "model").trim();
                                                                                        ModConfig cfg = ModConfig.get();
                                                                                        switch (value.toLowerCase(Locale.ROOT)) {
                                                                                                case "quality" -> cfg.groqModel = cfg.groqModelQuality;
                                                                                                case "fast" -> cfg.groqModel = cfg.groqModelFast;
                                                                                                default -> cfg.groqModel = value;
                                                                                        }
                                                                                        ModConfig.save();
                                                                                        feedback("§7[Ar2En] §aالنموذج الآن: §f" + cfg.groqModel);
                                                                                        return 1;
                                                                                })))
                                                .then(ClientCommands.literal("models")
                                                                .executes(ctx -> {
                                                                        ModConfig cfg = ModConfig.get();
                                                                        if (cfg.groqApiKey.isBlank()) {
                                                                                ctx.getSource().sendError(Component.literal("§7[Ar2En] §cأضف مفتاحك أولاً: /ar2en key <مفتاحك>"));
                                                                                return 0;
                                                                        }
                                                                        feedback("§7[Ar2En] §e⌛ جلب قائمة النماذج المتاحة لمفتاحك...");
                                                                        new GroqProvider(ChatTranslator.service().http()).listModels()
                                                                                        .whenComplete((list, err) -> Minecraft.getInstance().execute(() -> {
                                                                                                if (err != null) {
                                                                                                        chat("§7[Ar2En] §cفشل جلب النماذج: §f" + err.getMessage());
                                                                                                } else {
                                                                                                        chat("§7[Ar2En] §bالنماذج المتاحة لمفتاحك:");
                                                                                                        for (String line : list.split("\n")) {
                                                                                                                chat("§f  " + line);
                                                                                                        }
                                                                                                        chat("§7لتغيير النموذج: /ar2en model <الاسم من القائمة>");
                                                                                                }
                                                                                        }));
                                                                        return 1;
                                                                }))
                                                .then(ClientCommands.literal("provider")
                                                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                                                                .executes(ctx -> {
                                                                                        String name = StringArgumentType.getString(ctx, "name").toLowerCase(Locale.ROOT);
                                                                                        if (!List.of("groq", "libretranslate", "google").contains(name)) {
                                                                                                ctx.getSource().sendError(Component.literal("§7[Ar2En] §cمزود غير معروف. المتاح: groq | libretranslate | google"));
                                                                                                return 0;
                                                                                        }
                                                                                        ModConfig cfg = ModConfig.get();
                                                                                        if (cfg.providers.contains(name)) {
                                                                                                cfg.providers.remove(name);
                                                                                                feedback("§7[Ar2En] §eتم تعطيل المزود: §f" + name);
                                                                                        } else {
                                                                                                cfg.providers.add(name);
                                                                                                feedback("§7[Ar2En] §aتم تفعيل المزود: §f" + name);
                                                                                        }
                                                                                        ModConfig.save();
                                                                                        feedback("§7ترتيب المحاولة الآن: §f" + String.join(" ← ", cfg.providers));
                                                                                        return 1;
                                                                                })))
                                                .then(ClientCommands.literal("toggle")
                                                                .executes(ctx -> {
                                                                        ModConfig cfg = ModConfig.get();
                                                                        cfg.enabled = !cfg.enabled;
                                                                        ModConfig.save();
                                                                        feedback("§7[Ar2En] المود الآن: " + (cfg.enabled ? "§aمفعّل" : "§cمعطّل"));
                                                                        return 1;
                                                                }))
                                                .then(ClientCommands.literal("test")
                                                                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                                                                .executes(ctx -> {
                                                                                        String text = StringArgumentType.getString(ctx, "text").trim();
                                                                                        feedback("§7[Ar2En] §e⌛ جارٍ الترجمة...");
                                                                                        ChatTranslator.service().translate(text).whenComplete((result, err) ->
                                                                                                        Minecraft.getInstance().execute(() -> {
                                                                                                                if (err != null || result == null || !result.ok()) {
                                                                                                                        chat("§7[Ar2En] §cفشلت الترجمة: §f" + (err != null ? err.getMessage() : result.error()));
                                                                                                                } else {
                                                                                                                        chat("§7[" + result.provider() + "] §f" + result.text());
                                                                                                                }
                                                                                                        }));
                                                                                        return 1;
                                                                                })))
                                                .then(ClientCommands.literal("reload")
                                                                .executes(ctx -> {
                                                                        ModConfig.load();
                                                                        feedback("§7[Ar2En] §aأُعيد تحميل " + ModConfig.path().getFileName());
                                                                        return 1;
                                                                }))
                ));
        }

        private static void feedback(String message) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
        }

        private static void chat(String message) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.gui != null) {
                        // Since 26.3 the chat widget lives on the HUD, not on the Gui.
                        minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
                }
        }
}
