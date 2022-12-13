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
package org.cloud.sonic.agent.transport;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.tools.SpringTool;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * @author Eason
 * @date 2022/6/12 02:45
 */
@Slf4j
public class TransportConnectionThread implements Runnable {
    /**
     * second
     */
    public static final long DELAY = 10;

    public static final String THREAD_NAME = "transport-connection-thread";

    public static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    String serverHost = String.valueOf(SpringTool.getPropertiesValue("sonic.server.host"));
    Integer serverPort = Integer.valueOf(SpringTool.getPropertiesValue("sonic.server.port"));
    String key = String.valueOf(SpringTool.getPropertiesValue("sonic.agent.key"));

    boolean isRelease = Boolean.valueOf(SpringTool.getPropertiesValue("sonic.release-mode"));

    @Override
    public void run() {
        Thread.currentThread().setName(THREAD_NAME);
        if (TransportWorker.client == null) {
            if (!TransportWorker.isKeyAuth) {
                return;
            }
            String url = String.format("ws://%s:%d%s/websockets/agent/%s",
                    serverHost, serverPort, isRelease ? "/server" : "", key).replace(":80/", "/");
            URI uri = URI.create(url);
            TransportClient transportClient = new TransportClient(uri);
            transportClient.connect();
        } else {
            JSONObject ping = new JSONObject();
            ping.put("msg", "ping");
            TransportWorker.send(ping);
        }
    }
}
