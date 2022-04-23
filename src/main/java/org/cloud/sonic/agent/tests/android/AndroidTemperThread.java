package org.cloud.sonic.agent.tests.android;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.netty.NettyClientHandler;
import org.cloud.sonic.agent.netty.NettyThreadPool;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AndroidTemperThread extends Thread {
    @Override
    public void run() {
        while (NettyClientHandler.serverOnline) {
            IDevice[] deviceList = AndroidDeviceBridgeTool.getRealOnLineDevices();
            if (deviceList == null) {
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
                    int total = getInt(real.substring(13, real.indexOf("\n")));
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

    public int getInt(String a) {
        String regEx = "[^0-9]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(a);
        return Integer.parseInt(m.replaceAll("").trim());
    }
}
