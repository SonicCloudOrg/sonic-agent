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

import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.common.maps.GlobalProcessMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.Semaphore;

@Component
@Slf4j
public class SGMTool {
    private static final String pFile = new File("plugins").getAbsolutePath();
    private static final File sgmBinary = new File(pFile + File.separator + "sonic-go-mitmproxy");
    private static final String sgm = sgmBinary.getAbsolutePath();

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
            Semaphore isFinish = new Semaphore(0);
            new Thread(() -> {
                String s;
                while (true) {
                    try {
                        if ((s = stdInput.readLine()) == null) break;
                    } catch (IOException e) {
                        log.info(e.getMessage());
                        break;
                    }
                    if (s.contains("Proxy start listen")) {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException ignored) {
                        }
                        isFinish.release();
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
                log.info("{} web proxy done.", udId);
            }).start();
            int wait = 0;
            while (!isFinish.tryAcquire()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                wait++;
                if (wait >= 120) {
                    log.info("{} web proxy start timeout!", udId);
                    return;
                }
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
