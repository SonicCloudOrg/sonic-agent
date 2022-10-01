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
