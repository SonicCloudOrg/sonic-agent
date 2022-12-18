package org.cloud.sonic.agent.tools;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
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

    /**
     * 获取所有的ipv4
     * @return
     * @throws SocketException
     */
    public static List<String> getIpV4Address() throws SocketException {
        List<String> list = new LinkedList<>();
        Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
        while (enumeration.hasMoreElements()) {
            NetworkInterface network = (NetworkInterface) enumeration.nextElement();
            if (network.isVirtual() || !network.isUp()) {
                continue;
            } else {
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = (InetAddress) addresses.nextElement();
                    if (address != null && (address instanceof Inet4Address) && !address.isLoopbackAddress()) {
                        list.add(address.getHostAddress());
                    }
                }
            }
        }
        return list;
    }
    
}
