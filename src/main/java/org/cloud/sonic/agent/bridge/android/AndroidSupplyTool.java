package org.cloud.sonic.agent.bridge.android;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.maps.GlobalProcessMap;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;

import javax.websocket.Session;
import java.io.File;

public class AndroidSupplyTool {
    private static String sas = new File("plugins" + File.separator + "sonic-android-supply").getAbsolutePath();

    public static void startShare(String udId, Session session) {
        new Thread(() -> {
            stopShare(udId);
            String processName = String.format("process-%s-sas", udId);
            String commandLine = "%s share -s %s --translate-port %d";
            JSONObject sasJSON = new JSONObject();
            sasJSON.put("msg", "sas");
            sasJSON.put("isEnable", true);
            try {
                String system = System.getProperty("os.name").toLowerCase();
                Process ps = null;
                int port = PortTool.getPort();
                if (system.contains("win")) {
                    ps = Runtime.getRuntime().exec(new String[]{"cmd", "/c", String.format(commandLine, sas, udId, port)});
                } else if (system.contains("linux") || system.contains("mac")) {
                    ps = Runtime.getRuntime().exec(new String[]{"sh", "-c", String.format(commandLine, sas, udId, port)});
                }
                GlobalProcessMap.getMap().put(processName, ps);
                sasJSON.put("port", port);
            } catch (Exception e) {
                sasJSON.put("port", 0);
                e.printStackTrace();
            } finally {
                BytesTool.sendText(session, sasJSON.toJSONString());
            }
        }).start();
    }

    public static void stopShare(String udId) {
        String processName = String.format("process-%s-sas", udId);
        if (GlobalProcessMap.getMap().get(processName) != null) {
            Process ps = GlobalProcessMap.getMap().get(processName);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
    }
}
