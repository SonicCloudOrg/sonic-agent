package com.sonic.agent.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class ScrcpyTool {
    public static void main(String[] args) throws IOException {
        Socket capSocket = null;
        InputStream inputStream = null;
        capSocket = new Socket("localhost", 8666);
        inputStream = capSocket.getInputStream();
        while (capSocket.isConnected()) {
            byte[] buffer;
            int len = 0;
            while (len == 0) {
                len = inputStream.available();
            }
            buffer = new byte[len];
            inputStream.read(buffer);
            System.out.println(buffer);
        }
        System.out.println(1);
    }

    public void startScrcpyServer() {

    }
}
