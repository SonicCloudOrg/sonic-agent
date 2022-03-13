package org.cloud.sonic.agent.enums;

import java.io.Serializable;

/**
 * @author JayWenStar
 * @date 2022/3/13 1:49 下午
 */
public enum ConditionEnum implements SonicEnum<Integer>, Serializable {

    /**
     * 非条件
     */
    NONE(0),

    /**
     * if 条件
     */
    IF(1),

    /**
     * else if 条件
     */
    ELSE_IF(2),

    /**
     * else 条件
     */
    ELSE(3),

    /**
     * while 条件
     */
    WHILE(4);

    private Integer value = 0;

    ConditionEnum(int value) {
        this.value = value;
    }

    @Override
    public Integer getValue() {
        return value;
    }


}
