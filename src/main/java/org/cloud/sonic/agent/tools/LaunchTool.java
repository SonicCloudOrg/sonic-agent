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

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.common.maps.GlobalProcessMap;
import org.cloud.sonic.agent.common.maps.IOSProcessMap;
import org.cloud.sonic.agent.transport.TransportConnectionThread;
import org.cloud.sonic.agent.transport.TransportWorker;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
@Slf4j
public class LaunchTool implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        File testFile = new File("test-output");
        if (!testFile.exists()) {
            testFile.mkdirs();
        }
        ScheduleTool.scheduleAtFixedRate(
                new TransportConnectionThread(),
                TransportConnectionThread.DELAY,
                TransportConnectionThread.DELAY,
                TransportConnectionThread.TIME_UNIT
        );
        TransportWorker.readQueue();
        new Thread(() -> {
            File file = new File("plugins/sonic-go-mitmproxy-ca-cert.pem");
            if (!file.exists()) {
                log.info("Generating ca file...");
                SGMTool.startProxy("init", SGMTool.getCommand());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // 仅生成证书
                SGMTool.stopProxy("init");
                file = new File("plugins/sonic-go-mitmproxy-ca-cert.pem");
                if (!file.exists()) {
                    log.info("init sonic-go-mitmproxy-ca failed!");
                } else {
                    log.info("init sonic-go-mitmproxy-ca Successful!");
                }
            }
        }).start();
    }

    @PreDestroy
    public void destroy() {
        for (String key : GlobalProcessMap.getMap().keySet()) {
            Process ps = GlobalProcessMap.getMap().get(key);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
        for (String key : IOSProcessMap.getMap().keySet()) {
            List<Process> ps = IOSProcessMap.getMap().get(key);
            for (Process p : ps) {
                p.children().forEach(ProcessHandle::destroy);
                p.destroy();
            }
        }
        log.info("Release done!");
    }
}
