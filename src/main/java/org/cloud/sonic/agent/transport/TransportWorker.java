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
import org.cloud.sonic.agent.tools.BytesTool;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Configuration
@Slf4j
public class TransportWorker {
    private static LinkedBlockingQueue<JSONObject> dataQueue = new LinkedBlockingQueue<>();
    public static ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    public static TransportClient client = null;
    public static Boolean isKeyAuth = true;

    public static Boolean reconnect = false;

    public static void send(JSONObject jsonObject) {
        dataQueue.offer(jsonObject);
    }

    public static void readQueue() {
        cachedThreadPool.execute(() -> {
            while (isKeyAuth) {
                try {
                    if (client != null && client.isOpen()) {
                        if (!dataQueue.isEmpty()) {
                            JSONObject m = dataQueue.poll();
                            m.put("agentId", BytesTool.agentId);
                            client.send(m.toJSONString());
                        } else {
                            Thread.sleep(1000);
                        }
                    } else {
                        Thread.sleep(5000);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
