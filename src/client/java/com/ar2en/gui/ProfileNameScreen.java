package com.ar2en.gui;

import com.ar2en.ModConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Tiny dialog that asks for the name of a new profile, then snapshots the
 * current live settings (model + providers + prompt) into it.
 */
public class ProfileNameScreen extends Screen {

        private final Screen parent;
        private EditBox nameBox;

        public ProfileNameScreen(Screen parent) {
                super(Component.literal("بروفايل جديد"));
                this.parent = parent;
        }

        @Override
        protected void init() {
                addRenderableWidget(new StringWidget(0, 30, this.width, 12, this.title, this.font));
                addRenderableWidget(new StringWidget(0, 48, this.width, 10,
                                Component.literal("بطاقة تحفظ إعداداتك الحالية — مثال: سلانغ خفيف / رسمي / سيرفر معين"),
                                this.font));

                nameBox = new EditBox(this.font, this.width / 2 - 110, 70, 220, 20, Component.literal("اسم البروفايل"));
                nameBox.setMaxLength(40);
                nameBox.setHint(Component.literal("اكتب اسم البروفايل..."));
                addRenderableWidget(nameBox);
                setInitialFocus(nameBox);

                addRenderableWidget(Button.builder(Component.literal("إنشاء"), b -> create())
                                .bounds(this.width / 2 - 110, 102, 106, 20).build());
                addRenderableWidget(Button.builder(Component.literal("إلغاء"), b -> this.minecraft.setScreen(parent))
                                .bounds(this.width / 2 + 4, 102, 106, 20).build());
        }

        private void create() {
                ModConfig cfg = ModConfig.get();
                cfg.addProfile(nameBox.getValue());
                ModConfig.save();
                this.minecraft.setScreen(parent);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
                if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                        create();
                        return true;
                }
                return super.keyPressed(event);
        }

        @Override
        public void onClose() {
                this.minecraft.setScreen(parent);
        }
}
