/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
