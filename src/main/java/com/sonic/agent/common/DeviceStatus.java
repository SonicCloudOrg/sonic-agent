package com.sonic.agent.common;

/**
 * @author ZhouYiXun
 * @des 枚举设备状态
 * @date 2021/08/16 19:26
 */
public enum DeviceStatus {
    ONLINE("ONLINE"),
    OFFLINE("OFFLINE"),
    TESTING("TESTING"),
    DEBUGGING("DEBUGGING"),
    ERROR("ERROR");
    private String status;

    DeviceStatus(String status) {
        this.status = status;
    }
}
