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
package org.cloud.sonic.agent.automation;

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
