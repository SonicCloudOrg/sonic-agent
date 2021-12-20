package com.sonic.agent.tests.android;

import com.android.ddmlib.IDevice;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;

import java.util.List;

public class AndroidTemperThread extends Thread {
    @Override
    public void run() {
        while (true) {
            IDevice[] deviceList = AndroidDeviceBridgeTool.getRealOnLineDevices();
            for (IDevice iDevice : deviceList) {
                String temper = AndroidDeviceBridgeTool
                        .executeCommand(iDevice, "dumpsys battery")
                        .trim();
                String real = temper.substring(temper.indexOf("temperature"));
                float total = (float) Integer.parseInt(real.substring(13, real.indexOf("\n"))) / 10;
                System.out.println(iDevice.getSerialNumber() + "当前温度:" + total + "摄氏度");
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
