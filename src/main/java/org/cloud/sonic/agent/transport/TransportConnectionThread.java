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

    @Override
    public void run() {
        Thread.currentThread().setName(THREAD_NAME);
        if (TransportWorker.client == null) {
            if (!TransportWorker.isKeyAuth) {
                return;
            }
            //开发环境去掉/server
            String url = String.format("ws://%s:%d/websockets/agent/%s",
                    serverHost, serverPort, key).replace(":80/", "/");
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
