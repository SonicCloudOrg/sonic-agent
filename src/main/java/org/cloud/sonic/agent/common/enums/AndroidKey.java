/*
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.cloud.sonic.agent.common.enums;

public enum AndroidKey {
    UNKNOWN(0),
    SOFT_LEFT(1),
    SOFT_RIGHT(2),
    HOME(3),
    BACK(4),
    CALL(5),
    ENDCALL(6),
    STAR(17),
    POUND(18),
    DPAD_UP(19),
    DPAD_DOWN(20),
    DPAD_LEFT(21),
    DPAD_RIGHT(22),
    DPAD_CENTER(23),
    VOLUME_UP(24),
    VOLUME_DOWN(25),
    POWER(26),
    CAMERA(27),
    CLEAR(28),
    COMMA(55),
    PERIOD(56),
    EXPLORER(64),
    ENVELOPE(65),
    ENTER(66),
    DEL(67),
    GRAVE(68),
    MINUS(69),
    EQUALS(70),
    LEFT_BRACKET(71),
    RIGHT_BRACKET(72),
    BACKSLASH(73),
    SEMICOLON(74),
    APOSTROPHE(75),
    SLASH(76),
    AT(77),
    NUM(78),
    HEADSETHOOK(79),
    FOCUS(80),
    PLUS(81),
    MENU(82),
    NOTIFICATION(83),
    SEARCH(84),
    MEDIA_PLAY_PAUSE(85),
    MEDIA_STOP(86),
    MEDIA_NEXT(87),
    MEDIA_PREVIOUS(88),
    MEDIA_REWIND(89),
    MEDIA_FAST_FORWARD(90),
    MUTE(91),
    PAGE_UP(92),
    PAGE_DOWN(93),
    ESCAPE(111),
    FORWARD_DEL(112),
    CTRL_LEFT(113),
    CTRL_RIGHT(114),
    CAPS_LOCK(115),
    SCROLL_LOCK(116),
    META_LEFT(117),
    META_RIGHT(118),
    FUNCTION(119),
    SYSRQ(120),
    BREAK(121),
    INSERT(124),
    FORWARD(125),
    VOLUME_MUTE(164),
    INFO(165),
    WINDOW(171),
    PROG_BLUE(186),
    APP_SWITCH(187),
    LANGUAGE_SWITCH(204),
    MANNER_MODE(205),
    MODE_3D(206),
    CONTACTS(207),
    CALENDAR(208),
    MUSIC(209),
    CALCULATOR(210),
    BRIGHTNESS_DOWN(220),
    BRIGHTNESS_UP(221);

    private final int code;

    AndroidKey(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}