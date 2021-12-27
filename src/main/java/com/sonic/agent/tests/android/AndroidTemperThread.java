package com.sonic.agent.tests.android;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import com.sonic.agent.netty.NettyClientHandler;
import com.sonic.agent.netty.NettyThreadPool;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class AndroidTemperThread extends Thread {
    @Override
    public void run() {
        while (NettyClientHandler.serverOnline) {
            IDevice[] deviceList = AndroidDeviceBridgeTool.getRealOnLineDevices();
            if(deviceList==null){
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            List<JSONObject> detail = new ArrayList<>();
            for (IDevice iDevice : deviceList) {
                JSONObject jsonObject = new JSONObject();
                String temper = AndroidDeviceBridgeTool
                        .executeCommand(iDevice, "dumpsys battery");
                if (StringUtils.hasText(temper)) {
                    String real = temper.substring(temper.indexOf("temperature")).trim();
                    int total = Integer.parseInt(real.substring(13, real.indexOf("\n")));
                    jsonObject.put("udId", iDevice.getSerialNumber());
                    jsonObject.put("tem", total);
                    detail.add(jsonObject);
                }
            }
            JSONObject result = new JSONObject();
            result.put("msg", "temperature");
            result.put("detail", detail);
            NettyThreadPool.send(result);
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
