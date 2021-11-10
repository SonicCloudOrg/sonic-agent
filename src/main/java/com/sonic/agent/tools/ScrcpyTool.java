package com.sonic.agent.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ScrcpyTool {
    public static void main(String[] args) throws IOException {
        Socket capSocket;
        OutputStream outputStream;
        InputStream inputStream;
        capSocket = new Socket("localhost", 8666);
        outputStream = capSocket.getOutputStream();
        inputStream = capSocket.getInputStream();
        outputStream.write(0);
        while (capSocket.isConnected()) {
            System.out.println(inputStream.read());
        }
        System.out.println(1);
    }

    public void startScrcpyServer() {

    }
}
