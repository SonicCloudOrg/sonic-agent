package org.cloud.sonic.agent.enums;

/**
 * @author qilululi
 * 用于系统相关的参数
*/
public enum OSType {
    MacOS       ("MacOS"),
    Windows     ("Windows"),
    Linux       ("Linux"),
    ;

    String name;
    OSType(String name) {
        this.name = name;
    }

    public static OSType get(String name) {
        OSType result = null;
        try {
            result = OSType.valueOf(name);
        } catch (Exception e) {
        }

        return result;
    }
}
