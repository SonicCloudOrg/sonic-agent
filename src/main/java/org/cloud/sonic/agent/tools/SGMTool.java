package org.cloud.sonic.agent.tools;

import org.cloud.sonic.agent.common.maps.GlobalProcessMap;

import java.io.File;

public class SGMTool {
    public static String getCommand(int pPort, int webPort) {
        File pFile = new File("plugins");
        File sgm = new File(pFile + File.separator + "sonic-go-mitmproxy");
        String command = String.format(
                "%s -cert_path %s -addr :%d -web_addr :%d", sgm.getAbsolutePath(), pFile.getAbsolutePath(), pPort, webPort);
        return command;
    }

    public static String getCommand() {
        File pFile = new File("plugins");
        File sgm = new File(pFile + File.separator + "sonic-go-mitmproxy");
        String command = String.format(
                "%s -cert_path %s", sgm.getAbsolutePath(), pFile.getAbsolutePath());
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
