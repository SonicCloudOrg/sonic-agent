package org.cloud.sonic.agent.tools;

import java.util.ArrayList;
import java.util.List;

public class MacIpTool {
    /**
     * 获取网络列表信息
     *
     * networksetup -listallnetworkservices
     *
     * @return
     */
    public static List<String> networkServices() {
        List<String> result = new ArrayList<>();

        List<String> commandResult = null;

        // 获取所有的信息
        List<String> servicesByCmd = new ArrayList<>();
        servicesByCmd.add("networksetup");
        servicesByCmd.add("-listallnetworkservices");

        commandResult = CommandTool.exec(servicesByCmd);
        if (commandResult == null) {
            return result;
        }

        // 抽取有效的service
        for (int i = 1; i < commandResult.size(); i++) {
            String networkservice = commandResult.get(i);

            List<String> serviceByCmd = new ArrayList<>();
            serviceByCmd.add("networksetup");
            serviceByCmd.add("-getinfo");
            serviceByCmd.add(networkservice);

            List<String> subcommandResult = CommandTool.exec(serviceByCmd);
            if (subcommandResult == null) {
                return result;
            }

            boolean hasIp = false;
            for (String nwinfo : subcommandResult) {
                if (nwinfo.toLowerCase().indexOf("ip address") == 0) {
                    hasIp = true;
                    break;
                }
            }

            if (hasIp) {
                result.add(networkservice);
            }

        }

        return result;
    }
}
