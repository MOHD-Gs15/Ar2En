package com.ar2en.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Registers the config screen with ModMenu: when this mod is installed,
 * pressing the "Settings" button in Mod Menu opens Ar2EnConfigScreen.
 * The mod stays fully functional without ModMenu — this integration is optional.
 */
public class Ar2EnModMenu implements ModMenuApi {

        @Override
        public ConfigScreenFactory<?> getModConfigScreenFactory() {
                return Ar2EnConfigScreen::new;
        }
}
