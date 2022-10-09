package org.cloud.sonic.agent.bridge.android;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.common.maps.GlobalProcessMap;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.ProcessCommandTool;
import org.cloud.sonic.agent.tools.SpringTool;

import javax.websocket.Session;
import java.io.File;
import java.util.List;

@Slf4j
public class AndroidSupplyTool {
    private static String sas = new File("plugins" + File.separator + "sonic-android-supply").getAbsolutePath();
    private static String sasVersion = String.valueOf(SpringTool.getPropertiesValue("sonic.sas"));
    private static boolean isEnable = Boolean.valueOf(SpringTool.getPropertiesValue("modules.android.use-sas"));

    public static void startShare(String udId, Session session) {
        JSONObject sasJSON = new JSONObject();
        sasJSON.put("msg", "sas");
        if (isEnable) {
            sasJSON.put("isEnable", true);
            List<String> ver = ProcessCommandTool.getProcessLocalCommand(String.format("%s version", sas));
            if (ver.size() == 0 || !BytesTool.versionCheck(sasVersion, ver.get(0))) {
                log.info(String.format("Start sonic-android-supply failed! Please use [chmod -R 777 %s], if still failed, you can try with [sudo]", new File("plugins").getAbsolutePath()));
                sasJSON.put("port", 0);
                BytesTool.sendText(session, sasJSON.toJSONString());
                return;
            }
            stopShare(udId);
            String processName = String.format("process-%s-sas", udId);
            String commandLine = "%s share -s %s --translate-port %d";
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
        } else {
            sasJSON.put("isEnable", false);
            BytesTool.sendText(session, sasJSON.toJSONString());
        }
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
