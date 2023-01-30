/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published
 *   by the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.tests.handlers;

import com.alibaba.fastjson.JSONObject;

import java.util.Calendar;

/**
 * @author Eason
 */
public class TextHandler {
    public static String replaceTrans(String text, JSONObject globalParams) {
        if (text.contains("{{random}}")) {
            String random = (int) (Math.random() * 10 + Math.random() * 10 * 2) + 5 + "";
            text = text.replace("{{random}}", random);
        }
        if (text.contains("{{timestamp}}")) {
            String timeMillis = Calendar.getInstance().getTimeInMillis() + "";
            text = text.replace("{{timestamp}}", timeMillis);
        }
        if (text.contains("{{") && text.contains("}}")) {
            String tail = text.substring(text.indexOf("{{") + 2);
            if (tail.contains("}}")) {
                String child = tail.substring(tail.indexOf("}}") + 2);
                String middle = tail.substring(0, tail.indexOf("}}"));
                text = text.substring(0, text.indexOf("}}") + 2);
                if (globalParams.getString(middle) != null) {
                    text = text.replace("{{" + middle + "}}", globalParams.getString(middle));
                }
                text = text + replaceTrans(child, globalParams);
            }
        }
        return text;
    }
}
