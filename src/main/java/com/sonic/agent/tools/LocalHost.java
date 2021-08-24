package com.sonic.agent.tools;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

public class LocalHost {
    public static String host = "127.0.0.1";

    public static String getHostIp() {
        if (!host.equals("127.0.0.1")) {
            return host;
        }
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return host;
    }
}