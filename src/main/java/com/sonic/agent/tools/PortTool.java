package com.sonic.agent.tools;

import java.io.IOException;
import java.net.ServerSocket;

public class PortTool {
    public static Integer getPort() {
        ServerSocket serverSocket;
        int port = 0;
        try {
            serverSocket = new ServerSocket(0);
            port = serverSocket.getLocalPort();
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return port;
    }
}