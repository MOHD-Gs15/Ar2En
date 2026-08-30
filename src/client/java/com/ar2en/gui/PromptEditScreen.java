package com.ar2en.gui;

import com.ar2en.ChatTranslator;
import com.ar2en.ModConfig;
import com.ar2en.providers.GroqProvider;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Multi-line editor for the system prompt that is sent to the AI on every
 * translation. Saving the built-in prompt verbatim (or an empty text) falls
 * back to the built-in gamer slang prompt, so "استعادة الافتراضي" + save is a
 * full reset.
 */
public class PromptEditScreen extends Screen {

        private static final String DEFAULT_PROMPT = GroqProvider.systemPrompt().trim();

        private final Screen parent;
        private MultiLineEditBox box;

        public PromptEditScreen(Screen parent) {
                super(Component.literal("البرومبت المرسل للذكاء الاصطناعي"));
                this.parent = parent;
        }

        @Override
        protected void init() {
                ModConfig cfg = ModConfig.get();

                addRenderableWidget(new StringWidget(0, 10, this.width, 12, this.title, this.font));
                addRenderableWidget(new StringWidget(0, 27, this.width, 10,
                                Component.literal("هذه التعليمات تحدد أسلوب الترجمة — سلانغ القازرز افتراضياً"),
                                this.font));

                int boxWidth = Math.min(340, this.width - 40);
                box = MultiLineEditBox.builder()
                                .setPlaceholder(Component.literal("اكتب البرومبت المخصص هنا (بالإنجليزية ويفضل)..."))
                                .build(this.font, boxWidth, this.height - 104, Component.literal("البرومبت"));
                box.setX(this.width / 2 - boxWidth / 2);
                box.setY(42);
                box.setCharacterLimit(4000);
                box.setValue(cfg.customPrompt == null || cfg.customPrompt.isBlank()
                                ? DEFAULT_PROMPT
                                : cfg.customPrompt);
                addRenderableWidget(box);

                int bw = 104;
                int bx = this.width / 2 - (bw * 3 + 8) / 2;
                int by = this.height - 28;
                addRenderableWidget(Button.builder(Component.literal("حفظ"), b -> save())
                                .bounds(bx, by, bw, 20).build());
                addRenderableWidget(Button.builder(Component.literal("افتراضي"), b ->
                                                box.setValue(DEFAULT_PROMPT))
                                .bounds(bx + bw + 4, by, bw, 20)
                                .build());
                addRenderableWidget(Button.builder(Component.literal("إلغاء"), b ->
                                                this.minecraft.setScreen(parent))
                                .bounds(bx + 2 * (bw + 4), by, bw, 20).build());
        }

        private void save() {
                ModConfig cfg = ModConfig.get();
                String text = box.getValue().trim();
                // Identical to the built-in prompt or empty -> store "" (= use built-in).
                cfg.customPrompt = text.isEmpty() || text.equals(DEFAULT_PROMPT) ? "" : text;
                cfg.syncActiveProfile();
                ModConfig.save();
                ChatTranslator.service().clearCache();
                this.minecraft.setScreen(parent);
        }

        @Override
        public void onClose() {
                // Esc = discard edits, do not save.
                this.minecraft.setScreen(parent);
        }
}
