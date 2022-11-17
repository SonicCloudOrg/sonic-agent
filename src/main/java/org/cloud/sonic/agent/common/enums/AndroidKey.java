/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
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