/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published
 *   by the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.tools;

import org.cloud.sonic.agent.common.maps.GlobalProcessMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

@Component
public class SGMTool {
    private static final Logger logger = LoggerFactory.getLogger(SGMTool.class);
    private static String pFile = new File("plugins").getAbsolutePath();
    private static File sgmBinary = new File(pFile + File.separator + "sonic-go-mitmproxy");
    private static String sgm = sgmBinary.getAbsolutePath();
    private static String sgmVersion;
    @Value("${sonic.sgm}")
    private String ver;

    @Bean
    public void setSgmVer() {
        sgmVersion = ver;
    }

    public static void init() {
        sgmBinary.setExecutable(true);
        sgmBinary.setWritable(true);
        sgmBinary.setReadable(true);
        List<String> ver = ProcessCommandTool.getProcessLocalCommand(String.format("%s -version", sgm));
        if (ver.size() == 0 || !BytesTool.versionCheck(sgmVersion, ver.get(0).replace("sonic-go-mitmproxy:", "").trim())) {
            logger.info(String.format("Start sonic-go-mitmproxy failed! Please check sonic-go-mitmproxy version or use [chmod -R 777 %s], if still failed, you can try with [sudo]", pFile));
            AgentManagerTool.stop();
        }
    }

    public static String getCommand(int pPort, int webPort) {
        return String.format(
                "%s -cert_path %s -addr :%d -web_addr :%d", sgm, pFile, pPort, webPort);
    }

    public static String getCommand() {
        return String.format(
                "%s -cert_path %s", sgm, pFile);
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
            InputStreamReader inputStreamReader = new InputStreamReader(ps.getInputStream());
            BufferedReader stdInput = new BufferedReader(inputStreamReader);
            String s;
            while (true) {
                try {
                    if ((s = stdInput.readLine()) == null) break;
                } catch (IOException e) {
                    logger.info(e.getMessage());
                    break;
                }
                if (s.contains("Proxy start listen")) {
                    Thread.sleep(300);
                    break;
                }
            }
            try {
                stdInput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStreamReader.close();
            } catch (IOException e) {
                e.printStackTrace();
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
