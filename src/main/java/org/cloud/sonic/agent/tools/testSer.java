package org.cloud.sonic.agent.tools;

import com.fazecast.jSerialComm.SerialPort;

public class testSer {
    public static void main(String[] args) {
        System.out.println(SerialPort.getCommPorts().length);
    }
}
