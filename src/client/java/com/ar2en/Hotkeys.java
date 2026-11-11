package com.ar2en;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Hotkey helpers for Minecraft 26.3.
 *
 * <p>26.3 replaced GLFW with SDL, and <b>every</b> keyboard code changed value
 * (e.g. Right Shift: 344 -&gt; 229). The canonical key <i>names</i> are unchanged
 * though ("key.keyboard.right.shift"), so the hotkey is stored by name and the
 * legacy GLFW code stored by Ar2En &lt;= 1.1.0 is translated on the first launch.
 */
public final class Hotkeys {

        /** Canonical name of the default hotkey (Right Shift). */
        public static final String DEFAULT_NAME = "key.keyboard.right.shift";

        /** GLFW-era code of Right Shift, as written by Ar2En 1.1.0 on Minecraft 26.1.x. */
        public static final int LEGACY_DEFAULT_CODE = 344;

        /**
         * GLFW key code -&gt; canonical key name (Minecraft 26.1.x era).
         *
         * <p>Declared before {@link #LEGACY_NAMES} because static fields are initialised
         * in textual order.
         */
        private static final String[] LEGACY_ENTRIES = {
                "256:key.keyboard.escape",
                "257:key.keyboard.enter",
                "258:key.keyboard.tab",
                "259:key.keyboard.backspace",
                "260:key.keyboard.insert",
                "261:key.keyboard.delete",
                "262:key.keyboard.right",
                "263:key.keyboard.left",
                "264:key.keyboard.down",
                "265:key.keyboard.up",
                "266:key.keyboard.page.up",
                "267:key.keyboard.page.down",
                "268:key.keyboard.home",
                "269:key.keyboard.end",
                "280:key.keyboard.caps.lock",
                "281:key.keyboard.scroll.lock",
                "282:key.keyboard.num.lock",
                "283:key.keyboard.print.screen",
                "284:key.keyboard.pause",
                "290:key.keyboard.f1",
                "291:key.keyboard.f2",
                "292:key.keyboard.f3",
                "293:key.keyboard.f4",
                "294:key.keyboard.f5",
                "295:key.keyboard.f6",
                "296:key.keyboard.f7",
                "297:key.keyboard.f8",
                "298:key.keyboard.f9",
                "299:key.keyboard.f10",
                "300:key.keyboard.f11",
                "301:key.keyboard.f12",
                "302:key.keyboard.f13",
                "303:key.keyboard.f14",
                "304:key.keyboard.f15",
                "305:key.keyboard.f16",
                "306:key.keyboard.f17",
                "307:key.keyboard.f18",
                "308:key.keyboard.f19",
                "309:key.keyboard.f20",
                "310:key.keyboard.f21",
                "311:key.keyboard.f22",
                "312:key.keyboard.f23",
                "313:key.keyboard.f24",
                "32:key.keyboard.space",
                "320:key.keyboard.keypad.0",
                "321:key.keyboard.keypad.1",
                "322:key.keyboard.keypad.2",
                "323:key.keyboard.keypad.3",
                "324:key.keyboard.keypad.4",
                "325:key.keyboard.keypad.5",
                "326:key.keyboard.keypad.6",
                "327:key.keyboard.keypad.7",
                "328:key.keyboard.keypad.8",
                "329:key.keyboard.keypad.9",
                "330:key.keyboard.keypad.decimal",
                "332:key.keyboard.keypad.multiply",
                "334:key.keyboard.keypad.add",
                "335:key.keyboard.keypad.enter",
                "336:key.keyboard.keypad.equal",
                "340:key.keyboard.left.shift",
                "341:key.keyboard.left.control",
                "342:key.keyboard.left.alt",
                "343:key.keyboard.left.win",
                "344:key.keyboard.right.shift",
                "345:key.keyboard.right.control",
                "346:key.keyboard.right.alt",
                "347:key.keyboard.right.win",
                "39:key.keyboard.apostrophe",
                "44:key.keyboard.comma",
                "45:key.keyboard.minus",
                "46:key.keyboard.period",
                "47:key.keyboard.slash",
                "48:key.keyboard.0",
                "49:key.keyboard.1",
                "50:key.keyboard.2",
                "51:key.keyboard.3",
                "52:key.keyboard.4",
                "53:key.keyboard.5",
                "54:key.keyboard.6",
                "55:key.keyboard.7",
                "56:key.keyboard.8",
                "57:key.keyboard.9",
                "59:key.keyboard.semicolon",
                "61:key.keyboard.equal",
                "65:key.keyboard.a",
                "66:key.keyboard.b",
                "67:key.keyboard.c",
                "68:key.keyboard.d",
                "69:key.keyboard.e",
                "70:key.keyboard.f",
                "71:key.keyboard.g",
                "72:key.keyboard.h",
                "73:key.keyboard.i",
                "74:key.keyboard.j",
                "75:key.keyboard.k",
                "76:key.keyboard.l",
                "77:key.keyboard.m",
                "78:key.keyboard.n",
                "79:key.keyboard.o",
                "80:key.keyboard.p",
                "81:key.keyboard.q",
                "82:key.keyboard.r",
                "83:key.keyboard.s",
                "84:key.keyboard.t",
                "85:key.keyboard.u",
                "86:key.keyboard.v",
                "87:key.keyboard.w",
                "88:key.keyboard.x",
                "89:key.keyboard.y",
                "90:key.keyboard.z",
                "91:key.keyboard.left.bracket",
                "92:key.keyboard.backslash",
                "93:key.keyboard.right.bracket",
                "96:key.keyboard.grave.accent",
        };

        /** GLFW key code -&gt; canonical key name (Minecraft 26.1.x era). */
        private static final Map<Integer, String> LEGACY_NAMES = legacyNames();

        private Hotkeys() {
        }

        private static Map<Integer, String> legacyNames() {
                Map<Integer, String> map = new HashMap<>();
                for (String entry : LEGACY_ENTRIES) {
                        int sep = entry.indexOf(':');
                        map.put(Integer.parseInt(entry.substring(0, sep)), entry.substring(sep + 1));
                }
                return map;
        }

        /** Canonical name for a legacy (GLFW) key code, or null when the code is unknown. */
        public static String nameForLegacyCode(int legacyCode) {
                return LEGACY_NAMES.get(legacyCode);
        }

        /**
         * Resolves a canonical key name to a live key.
         * Returns null when the name is missing or unusable, so callers can fall back
         * to the default binding.
         */
        public static InputConstants.Key resolve(String canonicalName) {
                if (canonicalName == null || canonicalName.isBlank()) {
                        return null;
                }
                try {
                        InputConstants.Key key = InputConstants.getKey(canonicalName);
                        return key != null ? key : null;
                } catch (RuntimeException e) {
                        return null;
                }
        }

        /** The default key (Right Shift) as a live key object. */
        public static InputConstants.Key defaultKey() {
                return InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);
        }

        /** The canonical name of a key, suitable for storing in the config file. */
        public static String nameOf(InputConstants.Key key) {
                return key != null ? key.getName() : DEFAULT_NAME;
        }

        /** The current (SDL) key code of a key. */
        public static int codeOf(InputConstants.Key key) {
                return key != null ? key.getValue() : InputConstants.KEY_RSHIFT;
        }
}
