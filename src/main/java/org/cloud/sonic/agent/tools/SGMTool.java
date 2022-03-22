package org.cloud.sonic.agent.tools;

import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.common.maps.GlobalProcessMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
public class SGMTool {
    private static final Logger logger = LoggerFactory.getLogger(SGMTool.class);
    private static String pFile = new File("plugins").getAbsolutePath();
    private static String sgm = new File(pFile + File.separator + "sonic-go-mitmproxy").getAbsolutePath();
    private static String sgmVersion;
    @Value("${sonic.sgm}")
    private String ver;

    @Bean
    public void setSgmVer(){
        sgmVersion = ver;
    }

    public static void init() {
        List<String> ver = ProcessCommandTool.getProcessLocalCommand(String.format("%s -version", sgm));
        if (ver.size() == 0 || !ver.get(0).equals(String.format("sonic-go-mitmproxy: %s",sgmVersion))) {
            logger.info(String.format("启动sonic-go-mitmproxy失败！请执行 chmod -R 777 %s，仍然失败可加上sudo尝试", new File("").getAbsolutePath()));
            System.exit(0);
        }
    }

    public static String getCommand(int pPort, int webPort) {
        String command = String.format(
                "%s -cert_path %s -addr :%d -web_addr :%d", sgm, pFile, pPort, webPort);
        return command;
    }

    public static String getCommand() {
        String command = String.format(
                "%s -cert_path %s", sgm, pFile);
        return command;
    }

    public static void startProxy(String udId, String command) {
        String processName = String.format("process-%s-proxy", udId);
        if (GlobalProcessMap.getMap().get(processName) != null) {
            Process ps = GlobalProcessMap.getMap().get(processName);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
        String system = System.getProperty("os.name").toLowerCase();
        Process ps = null;
        try {
            if (system.contains("win")) {
                ps = Runtime.getRuntime().exec(new String[]{"cmd", "/c", command});
            } else if (system.contains("linux") || system.contains("mac")) {
                ps = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            }
            GlobalProcessMap.getMap().put(processName, ps);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void stopProxy(String udId) {
        String processName = String.format("process-%s-proxy", udId);
        if (GlobalProcessMap.getMap().get(processName) != null) {
            Process ps = GlobalProcessMap.getMap().get(processName);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
    }
}
