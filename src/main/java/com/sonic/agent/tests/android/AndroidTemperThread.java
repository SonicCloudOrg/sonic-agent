package com.sonic.agent.tests.android;

import com.android.ddmlib.IDevice;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;

import java.util.List;

public class AndroidTemperThread extends Thread{
    @Override
    public void run() {
        IDevice[] deviceList = AndroidDeviceBridgeTool.getRealOnLineDevices();
        for(IDevice iDevice:deviceList){

        }
    }
}
