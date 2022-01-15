package com.sonic.agent.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class TestTool {
    public static void main(String[] args) throws IOException {
        Socket capSocket =  new Socket("localhost", 8666);
        OutputStream outputStream;
        InputStream inputStream;
        outputStream = capSocket.getOutputStream();
        inputStream = capSocket.getInputStream();
        while (true){
            System.out.println(inputStream.read());
        }
    }
}
