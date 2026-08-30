package com.ar2en.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the chat input field so the translator can read and swap its text. */
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {

        @Accessor("input")
        EditBox ar2en$input();
}
