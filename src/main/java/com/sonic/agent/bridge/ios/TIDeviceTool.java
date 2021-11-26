package com.sonic.agent.bridge.ios;

import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import com.sonic.agent.maps.IOSProcessMap;
import com.sonic.agent.tools.PortTool;
import com.sonic.agent.tools.ProcessCommandTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@ConditionalOnProperty(value = "modules.ios.enable", havingValue = "true")
@DependsOn({"iOSThreadPoolInit", "nettyMsgInit"})
@Component
public class TIDeviceTool {
    private static final Logger logger = LoggerFactory.getLogger(TIDeviceTool.class);
    @Value("${modules.ios.wda-bundle-id}")
    private String getBundleId;
    private static String bundleId;

    @Bean
    public void setEnv() {
        bundleId = getBundleId;
    }

    public TIDeviceTool() {
        logger.info("开启iOS相关功能");
        init();
    }

    public static void init() {
        IOSDeviceThreadPool.cachedThreadPool.execute(() -> {
//            List<String> aDevice = ProcessCommandTool.getProcessLocalCommand("tidevice list --json");
//            for (String json : aDevice) {
//                sendOnlineStatus(udId);
//            }
//            while (true) {
//                try {
//                    Thread.sleep(3000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                List<String> newList = ProcessCommandTool.getProcessLocalCommand("tidevice list --json");
//                if (!aDevice.equals(newList)) {
//                    for (String udId : newList) {
//                        if (!aDevice.contains(udId)) {
//                            sendOnlineStatus(udId);
//                        }
//                    }
//                    for (String udId : aDevice) {
//                        if (!newList.contains(udId)) {
//                            sendDisConnectStatus(udId);
//                        }
//                    }
//                    aDevice = newList;
//                }
//            }
        });
        logger.info("iOS设备监听已开启");
    }

    public static int startWda(String udId) throws IOException, InterruptedException {
        synchronized (TIDeviceTool.class) {
            int port = PortTool.getPort();
            Process wdaProcess = Runtime.getRuntime().exec("tidevice -u " + udId +
                    " wdaproxy" + " -B " + bundleId +
                    " --port " + port);
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(wdaProcess.getInputStream()));
            String s;
            while ((s = stdInput.readLine()) != null) {
                if (s.contains("WebDriverAgent start successfully")) {
                    logger.info(udId + " wda启动完毕！");
                    break;
                } else {
                    Thread.sleep(500);
                }
            }
            List<Process> processList;
            if (IOSProcessMap.getMap().get(udId) != null) {
                processList = IOSProcessMap.getMap().get(udId);
                for (Process p : processList) {
                    if (p != null) {
                        p.destroy();
                    }
                }
            }
            processList = new ArrayList<>();
            processList.add(wdaProcess);
            IOSProcessMap.getMap().put(udId, processList);
            return port;
        }
    }

    public static int relayImg(String udId) throws IOException {
        int port = PortTool.getPort();
        Process relayProcess = Runtime.getRuntime().exec("tidevice -u " + udId +
                " relay " + port + " " + 9100);
        List<Process> processList;
        if (IOSProcessMap.getMap().get(udId) != null) {
            processList = IOSProcessMap.getMap().get(udId);
        } else {
            processList = new ArrayList<>();
        }
        processList.add(relayProcess);
        IOSProcessMap.getMap().put(udId, processList);
        return port;
    }

}
