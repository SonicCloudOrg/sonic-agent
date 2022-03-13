package org.cloud.sonic.agent.tools;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

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

    public static Socket getBindSocket() {
        Socket socket = new Socket();
        InetSocketAddress inetAddress = new InetSocketAddress(0);
        try {
            socket.bind(inetAddress);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return socket;
    }

    public static int releaseAndGetPort(Socket socket) {
        int port = socket.getLocalPort();
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return port;
    }
}