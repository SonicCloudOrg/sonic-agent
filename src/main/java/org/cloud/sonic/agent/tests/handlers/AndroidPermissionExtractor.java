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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android端权限解析工具类方法
 * Created by fengbincao on 2023/07/12
 */
public class AndroidPermissionExtractor {

    /**
     * 从dumpsys package的结果输出解析权限列表
     *
     * @param dumpsysOutput dumpsys package的结果输出
     * @param groupNames    权限组名
     * @param grantedState  是否获取授权状态
     * @return 解析出的权限列表
     */
    public static List<AndroidPermissionItem> extractPermissions(String dumpsysOutput, List<String> groupNames,
                                                                 Boolean grantedState) {
        List<AndroidPermissionItem> result = new ArrayList<>();

        for (String groupName : groupNames) {
            Pattern groupPattern = Pattern.compile("^(\\s*" + Pattern.quote(groupName) + " permissions:[\\s\\S]+)", Pattern.MULTILINE);
            Matcher groupMatcher = groupPattern.matcher(dumpsysOutput);

            if (!groupMatcher.find()) {
                continue;
            }

            String groupMatch = groupMatcher.group(1);
            String[] lines = groupMatch.split("\n");

            if (lines.length < 2) {
                continue;
            }

            int titleIndent = lines[0].indexOf(lines[0].trim());

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                int currentIndent = line.indexOf(line.trim());

                if (currentIndent <= titleIndent) {
                    break;
                }

                Pattern permissionNamePattern = Pattern.compile("android\\.\\w*\\.?permission\\.\\w+");
                Matcher permissionNameMatcher = permissionNamePattern.matcher(line);

                if (!permissionNameMatcher.find()) {
                    continue;
                }

                String permissionName = permissionNameMatcher.group();
                AndroidPermissionItem item = new AndroidPermissionItem(permissionName);

                if (grantedState != null) {
                    Pattern grantedStatePattern = Pattern.compile("\\bgranted=(\\w+)");
                    Matcher grantedStateMatcher = grantedStatePattern.matcher(line);

                    if (grantedStateMatcher.find()) {
                        boolean isGranted = grantedStateMatcher.group(1).equals("true");
                        item.setGranted(isGranted);
                    }
                }

                result.add(item);
            }
        }

        return result;
    }
}
