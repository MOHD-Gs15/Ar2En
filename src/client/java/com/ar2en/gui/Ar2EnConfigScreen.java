package com.ar2en.gui;

import com.ar2en.Ar2EnClient;
import com.ar2en.ChatTranslator;
import com.ar2en.ModConfig;
import com.ar2en.providers.GroqProvider;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Full in-game settings screen, opened from Mod Menu (or any future button).
 * Everything the old commands could do is editable here:
 * enable/disable, profiles, model (+ live fetch from Groq), hotkey rebinding,
 * system prompt editor, Groq API key and the provider fallback chain.
 */
public class Ar2EnConfigScreen extends Screen {

        private static final int W = 320;   // content width
        private static final int COL = 158; // column width
        private static final int ROW_H = 20;
        private static final List<String> PROVIDER_ORDER = List.of("groq", "libretranslate", "google");

        private final Screen parent;

        private boolean listeningForKey = false;
        private boolean internalUpdate = false;

        private Button hotkeyButton;
        private Button fetchButton;
        private StringWidget statusWidget;

        public Ar2EnConfigScreen(Screen parent) {
                super(Component.literal("إعدادات Ar2En — ترجمة الشات"));
                this.parent = parent;
        }

        @Override
        protected void init() {
                ModConfig cfg = ModConfig.get();
                int x0 = this.width / 2 - W / 2;
                int xL = x0;
                int xR = x0 + COL + 4;
                int y = 38;

                addRenderableWidget(new StringWidget(0, 14, this.width, 12, this.title, this.font));

                // ---- row 1: master toggle ----
                addRenderableWidget(withTooltip(CycleButton.booleanBuilder(
                                Component.literal("مفعّل"), Component.literal("معطّل"), cfg.enabled)
                                .create(xL, y, W, ROW_H, Component.literal("الترجمة"), (btn, val) -> {
                                        cfg.enabled = val;
                                        ModConfig.save();
                                }), "شغّل أو أطفئ المود بالكامل (نفس /ar2en toggle)"));
                y += ROW_H + 4;

                // ---- row 2: profile switcher + model switcher ----
                List<String> names = cfg.profiles.stream().map(p -> p.name).toList();
                int initialProfile = Math.max(0, Math.min(cfg.activeProfile, names.size() - 1));
                addRenderableWidget(withTooltip(CycleButton.builder(n -> Component.literal(String.valueOf(n)), names.get(initialProfile))
                                .withValues(names)
                                .create(xL, y, COL, ROW_H, Component.literal("البروفايل"), (btn, val) -> {
                                        int idx = names.indexOf(val);
                                        if (idx >= 0) {
                                                cfg.applyProfile(idx);
                                                ModConfig.save();
                                                ChatTranslator.service().clearCache();
                                        }
                                        // refresh the model button + prompt label to the applied profile
                                        this.minecraft.execute(this::rebuildWidgets);
                                }), "البروفايل = بطاقة محفوظة تجمع (الموديل + المزودين + البرومبت)"));

                addRenderableWidget(withTooltip(CycleButton.builder(m -> Component.literal(String.valueOf(m)), cfg.groqModel)
                                .withValues(List.copyOf(cfg.knownModels))
                                .create(xR, y, COL, ROW_H, Component.literal("الموديل"), (btn, val) -> {
                                        cfg.groqModel = val;
                                        cfg.syncActiveProfile();
                                        ModConfig.save();
                                        ChatTranslator.service().clearCache();
                                }), "نموذج الذكاء الاصطناعي — استعمل جلب النماذج لتحديث القائمة"));
                y += ROW_H + 4;

                // ---- row 3: add/delete profile + fetch models ----
                addRenderableWidget(Button.builder(Component.literal("+ جديد"), b ->
                                                this.minecraft.setScreen(new ProfileNameScreen(this)))
                                .bounds(xL, y, 77, ROW_H)
                                .tooltip(Tooltip.create(Component.literal("أنشئ بروفايل جديداً من إعداداتك الحالية")))
                                .build());
                addRenderableWidget(Button.builder(Component.literal("حذف"), b -> {
                                                cfg.deleteActiveProfile();
                                                ModConfig.save();
                                                ChatTranslator.service().clearCache();
                                                this.minecraft.execute(this::rebuildWidgets);
                                        })
                                .bounds(xL + 81, y, 77, ROW_H)
                                .tooltip(Tooltip.create(Component.literal("احذف البروفايل المختار حالياً")))
                                .build());

                fetchButton = Button.builder(Component.literal("جلب النماذج"), b -> fetchModels())
                                .bounds(xR, y, COL, ROW_H)
                                .tooltip(Tooltip.create(Component.literal(
                                                "Groq يغيّر نماذجه باستمرار — هذا الزر يجلب القائمة الحقيقية المتاحة لمفتاحك")))
                                .build();
                addRenderableWidget(fetchButton);
                y += ROW_H + 4;

                // ---- row 4: hotkey rebinding + prompt editor ----
                hotkeyButton = Button.builder(keyLabel(cfg), b -> {
                                                listeningForKey = true;
                                                hotkeyButton.setMessage(Component.literal("اضغط أي زر... (Esc للإلغاء)"));
                                        })
                                .bounds(xL, y, COL, ROW_H)
                                .tooltip(Tooltip.create(Component.literal(
                                                "المفتاح الذي تضغطه داخل الشات لترجمة النص العربي (افتراضياً Right Shift)")))
                                .build();
                addRenderableWidget(hotkeyButton);

                String promptLabel = cfg.customPrompt.isBlank() ? "البرومبت: افتراضي" : "البرومبت: مخصص ✓";
                addRenderableWidget(Button.builder(Component.literal(promptLabel), b ->
                                                this.minecraft.setScreen(new PromptEditScreen(this)))
                                .bounds(xR, y, COL, ROW_H)
                                .tooltip(Tooltip.create(Component.literal(
                                                "حرّر التعليمات المرسلة للذكاء الاصطناعي — تغيّر أسلوب الترجمة بالكامل")))
                                .build());
                y += ROW_H + 4;

                // ---- row 5: Groq API key ----
                EditBox keyBox = new EditBox(this.font, xL, y, W, ROW_H, Component.literal("مفتاح Groq API"));
                keyBox.setMaxLength(200);
                keyBox.setValue(cfg.groqApiKey);
                keyBox.setResponder(s -> cfg.groqApiKey = s.trim());
                keyBox.setHint(Component.literal("مفتاح Groq API (مجاني من console.groq.com)"));
                keyBox.setTooltip(Tooltip.create(Component.literal(
                                "ينشئ مفتاحاً مجانياً من console.groq.com ثم الصقه هنا — يُحفظ محلياً على جهازك فقط")));
                addRenderableWidget(keyBox);
                y += ROW_H + 4;

                // ---- row 6: provider fallback chain ----
                int pw = (W - 8) / 3;
                for (int i = 0; i < PROVIDER_ORDER.size(); i++) {
                        String p = PROVIDER_ORDER.get(i);
                        boolean on = cfg.providers.contains(p);
                        int px = xL + i * (pw + 4);
                        addRenderableWidget(withTooltip(CycleButton.booleanBuilder(
                                        Component.literal(p + " ✓"), Component.literal(p + " ✗"), on)
                                        .displayOnlyValue()
                                        .create(px, y, pw, ROW_H, Component.literal(p), (btn, val) -> {
                                                if (!val && cfg.providers.contains(p) && cfg.providers.size() <= 1) {
                                                        if (!internalUpdate) {
                                                                internalUpdate = true;
                                                                btn.setValue(true);
                                                                internalUpdate = false;
                                                        }
                                                        return; // never allow an empty chain
                                                }
                                                toggleProvider(p, val);
                                        }), "سلسلة المحاولة: " + String.join(" ← ", cfg.providers)
                                                + " — إذا فشل الأول ينتقل تلقائياً للثاني"));
                }

                // ---- status line + done ----
                statusWidget = new StringWidget(x0, this.height - 44, W, 10, Component.empty(), this.font);
                addRenderableWidget(statusWidget);

                addRenderableWidget(Button.builder(Component.literal("تم — حفظ وإغلاق"), b -> onClose())
                                .bounds(x0, this.height - 28, W, ROW_H)
                                .build());
        }

        private static <T extends AbstractWidget> T withTooltip(T widget, String tooltip) {
                widget.setTooltip(Tooltip.create(Component.literal(tooltip)));
                return widget;
        }

        private void toggleProvider(String p, boolean on) {
                ModConfig cfg = ModConfig.get();
                if (on) {
                        if (!cfg.providers.contains(p)) {
                                // rebuild the chain keeping the canonical fallback order
                                List<String> updated = new ArrayList<>();
                                for (String cand : PROVIDER_ORDER) {
                                        if (cand.equals(p) || cfg.providers.contains(cand)) {
                                                updated.add(cand);
                                        }
                                }
                                cfg.providers = updated;
                        }
                } else {
                        cfg.providers.remove(p);
                }
                cfg.syncActiveProfile();
                ModConfig.save();
                ChatTranslator.service().clearCache();
        }

        private void fetchModels() {
                ModConfig cfg = ModConfig.get();
                if (cfg.groqApiKey.isBlank()) {
                        setStatus("أضف مفتاح Groq أولاً (الخانة بالأسفل)");
                        return;
                }
                fetchButton.active = false;
                fetchButton.setMessage(Component.literal("⌛ جلب..."));
                new GroqProvider(ChatTranslator.service().http()).listModels()
                                .whenComplete((list, err) -> this.minecraft.execute(() -> {
                                        if (err != null) {
                                                String msg = err.getMessage() == null ? "خطأ" : err.getMessage();
                                                setStatus("فشل الجلب: " + msg);
                                                fetchButton.active = true;
                                                fetchButton.setMessage(Component.literal("جلب النماذج"));
                                                return;
                                        }
                                        LinkedHashSet<String> set = new LinkedHashSet<>();
                                        for (String line : list.split("\n")) {
                                                String m = line.trim();
                                                if (!m.isEmpty()) {
                                                        set.add(m);
                                                }
                                        }
                                        set.remove(cfg.groqModel);
                                        List<String> merged = new ArrayList<>();
                                        merged.add(cfg.groqModel); // keep the current model first
                                        merged.addAll(set);
                                        cfg.knownModels = merged;
                                        ModConfig.save();
                                        setStatus("تم جلب " + merged.size() + " نموذجاً متاحاً لمفتاحك ✓");
                                        this.minecraft.execute(this::rebuildWidgets);
                                }));
        }

        private void setStatus(String s) {
                if (statusWidget != null) {
                        statusWidget.setMessage(Component.literal(s));
                }
        }

        private static Component keyLabel(ModConfig cfg) {
                KeyMapping km = Ar2EnClient.translateKey();
                String name = km != null ? km.getTranslatedKeyMessage().getString() : "؟";
                return Component.literal("المفتاح: " + name);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
                if (listeningForKey) {
                        int key = event.key();
                        if (key == GLFW.GLFW_KEY_ESCAPE) {
                                // cancel, keep old binding
                                listeningForKey = false;
                                hotkeyButton.setMessage(keyLabel(ModConfig.get()));
                        } else if (key != 0) {
                                ModConfig cfg = ModConfig.get();
                                cfg.hotkeyCode = key;
                                Ar2EnClient.translateKey().setKey(InputConstants.Type.KEYSYM.getOrCreate(key));
                                ModConfig.save();
                                listeningForKey = false;
                                hotkeyButton.setMessage(keyLabel(cfg));
                        }
                        return true; // swallow everything while waiting for the new key
                }
                return super.keyPressed(event);
        }

        @Override
        public void onClose() {
                ModConfig.save();
                this.minecraft.setScreen(parent);
        }
}
