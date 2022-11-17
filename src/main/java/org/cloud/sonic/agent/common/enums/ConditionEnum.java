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

import java.io.Serializable;

/**
 * @author JayWenStar
 * @date 2022/3/13 1:49 下午
 */
public enum ConditionEnum implements SonicEnum<Integer>, Serializable {

    /**
     * 非条件
     */
    NONE(0, "none"),

    /**
     * if 条件
     */
    IF(1, "if"),

    /**
     * else if 条件
     */
    ELSE_IF(2, "else_if"),

    /**
     * else 条件
     */
    ELSE(3, "else"),

    /**
     * while 条件
     */
    WHILE(4, "while");

    private final Integer value;

    private final String name;

    ConditionEnum(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @Override
    public Integer getValue() {
        return value;
    }

    public String getName() {
        return name;
    }
}
