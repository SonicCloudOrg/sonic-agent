package org.cloud.sonic.agent.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ScrcpyTool {
    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final int READ_BUFFER_SIZE = 1024 * 5;

    public static void main(String[] args) throws IOException, InterruptedException {
        Socket capSocket =  new Socket("localhost", 8666);
        OutputStream outputStream;
        InputStream inputStream;
        outputStream = capSocket.getOutputStream();
        inputStream = capSocket.getInputStream();
        outputStream.write(0);

        int readLength;
        int naluIndex = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        int bufferLength = 0;
        while (capSocket.isConnected()) {
            readLength = inputStream.read(buffer, bufferLength, READ_BUFFER_SIZE);
//            System.out.println(inputStream.read());
            System.out.println(readLength);
            if(readLength>0) {
                System.out.println(readLength);
                bufferLength += readLength;
                System.out.println(readLength);
                for (int i = 5; i < bufferLength - 4; i++) {
                    if (buffer[i] == 0x00 &&
                            buffer[i + 1] == 0x00 &&
                            buffer[i + 2] == 0x00 &&
                            buffer[i + 3] == 0x01
                    ){
                        naluIndex = i;

                        byte[] naluBuffer = new byte[naluIndex];
                        System.arraycopy(buffer, 0, naluBuffer, 0, naluIndex);
//                        dataQueue.add(naluBuffer);
                        bufferLength -= naluIndex;
                        System.arraycopy(buffer, naluIndex, buffer, 0, bufferLength);
                        i = 5;
                    }
                }

            }
        }
    }

    public void startScrcpyServer() {

    }
}
