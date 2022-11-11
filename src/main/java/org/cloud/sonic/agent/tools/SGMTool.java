/*
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.cloud.sonic.agent.tools;

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
            logger.info(String.format("Start sonic-go-mitmproxy failed! Please use [chmod -R 777 %s], if still failed, you can try with [sudo]", pFile));
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
