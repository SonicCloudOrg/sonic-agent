/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2023 SonicCloudOrg
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

import java.util.List;

/**
 * Created by fengbincao on 2023/05/20
 */
public class AssertUtil {

    /**
     * 数量断言，泛型方法
     *
     * @param operation     操作类型
     * @param expectedCount 期望数量
     * @param elementList   元素列表
     * @param <T>           范型参数
     * @return 比较结果
     */
    public <T> boolean assertElementNum(String operation, int expectedCount, List<T> elementList) {
        boolean isSuccess;
        if (elementList == null || elementList.size() == 0) {
            isSuccess = switch (operation) {
                case "<" -> expectedCount > 0;
                case "<=" -> true;
                case ">" -> false;
                default -> expectedCount == 0;
            };
        } else {
            isSuccess = switch (operation) {
                case "<" -> elementList.size() < expectedCount;
                case "<=" -> elementList.size() <= expectedCount;
                case ">" -> elementList.size() > expectedCount;
                case ">=" -> elementList.size() >= expectedCount;
                default -> elementList.size() == expectedCount;
            };
        }
        return isSuccess;
    }

    public String getAssertDesc(String originOpe) {
        switch (originOpe) {
            case "equal" -> {
                return "等于";
            }
            case "notEqual" -> {
                return "不等于";
            }
            case "contain" -> {
                return "包含";
            }
            default -> {
                return "不包含";
            }
        }
    }
}
