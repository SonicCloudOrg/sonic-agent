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
package org.cloud.sonic.agent.tests;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.common.interfaces.StepType;
import org.cloud.sonic.agent.common.maps.WebSocketSessionMap;
import org.cloud.sonic.agent.registry.zookeeper.AgentZookeeperRegistry;
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.cloud.sonic.common.tools.SpringTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author ZhouYiXun
 * @des log工具类，会发送到服务端入库
 * @date 2021/8/16 19:54
 */
public class LogUtil {
    private final Logger logger = LoggerFactory.getLogger(LogUtil.class);
    public String sessionId = "";
    public String type;
    public int caseId = 0;
    public int resultId = 0;
    public String udId = "";

    /**
     * @param message
     * @return void
     * @author ZhouYiXun
     * @des 判断发送到哪个地方
     * @date 2021/8/16 19:57
     */
    public void send(JSONObject message) {
        //先加上消息附带信息
        message.put("cid", caseId);
        message.put("rid", resultId);
        message.put("udId", udId);
        if (type.equals(DeviceStatus.DEBUGGING)) {
            sendToWebSocket(WebSocketSessionMap.getSession(sessionId), message);
        }
        if (type.equals(DeviceStatus.TESTING)) {
            sendToServer(message);
        }
        logger.info(message.toJSONString());
    }

    /**
     * @param message
     * @return void
     * @author ZhouYiXun
     * @des 发送到服务端
     * @date 2021/8/16 19:57
     */
    private void sendToServer(JSONObject message) {
        message.put("time", new Date());
        message.put("agentId", AgentZookeeperRegistry.currentAgent.getId());
        SpringTool.getBean(AgentManagerTool.class).saveByTransport(message);
    }

    /**
     * @param session
     * @param message
     * @return void
     * @author ZhouYiXun
     * @des 通过session发送给前端
     * @date 2021/8/16 19:57
     */
    private void sendToWebSocket(Session session, JSONObject message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        synchronized (session) {
            try {
                message.put("time", getDateToString());
                session.getBasicRemote().sendText(message.toJSONString());
            } catch (IllegalStateException | IOException e) {
                logger.error(e.getMessage());
            }
        }
    }

    /**
     * @return java.lang.String
     * @author ZhouYiXun
     * @des format一下时间
     * @date 2021/8/16 19:58
     */
    public String getDateToString() {
        SimpleDateFormat sf = new SimpleDateFormat("HH:mm:ss");
        return sf.format(new Date());
    }

    /**
     * @param totalTime
     * @param platform
     * @param version
     * @return void
     * @author ZhouYiXun
     * @des 发送运行时长
     * @date 2021/8/16 19:58
     */
    public void sendElapsed(int totalTime, int platform, String version) {
        JSONObject log = new JSONObject();
        log.put("msg", "elapsed");
        log.put("pf", platform);
        log.put("ver", version);
        log.put("run", totalTime);
        send(log);
    }

    /**
     * @param status
     * @param des
     * @param detail
     * @return void
     * @author ZhouYiXun
     * @des 发送普通步骤log
     * @date 2021/8/16 19:58
     */
    public void sendStepLog(int status, String des, String detail) {
        JSONObject log = new JSONObject();
        log.put("msg", "step");
        log.put("des", des);
        log.put("status", status);
        log.put("log", detail);
        send(log);
    }

    /**
     * @param type
     * @param detail
     * @return void
     * @author ZhouYiXun
     * @des 发送性能数据
     * @date 2021/8/16 19:58
     */
    public void sendPerLog(String packageName, int type, JSONObject detail) {
        JSONObject log = new JSONObject();
        log.put("msg", "perform");
        log.put("des", packageName);
        log.put("log", detail.toJSONString());
        log.put("status", type);
        send(log);
    }

    /**
     * @param isSupport 是否支持录像
     * @param url
     * @return void
     * @author ZhouYiXun
     * @des 发送录像数据
     * @date 2021/8/16 19:58
     */
    public void sendRecordLog(boolean isSupport, String fileName, String url) {
        JSONObject log = new JSONObject();
        log.put("msg", "record");
        log.put("status", isSupport ? 1 : 0);
        log.put("des", fileName);
        log.put("log", url);
        send(log);
    }

    /**
     * @param url
     * @return void
     * @author ZhouYiXun
     * @des 发送日志数据
     * @date 2021/8/26 19:58
     */
//    public void sendSelfLog(String fileName, String url) {
//        JSONObject log = new JSONObject();
//        log.put("msg", "log");
//        log.put("name", fileName);
//        log.put("url", url);
//        send(log);
//    }

    /**
     * @param status
     * @return void
     * @author ZhouYiXun
     * @des 发送测试状态
     * @date 2021/8/16 19:58
     */
    public void sendStatusLog(int status) {
        JSONObject log = new JSONObject();
        log.put("msg", "status");
        log.put("des", "");
        log.put("log", "");
        log.put("status", status);
        send(log);
    }

    /**
     * @param platform
     * @param version
     * @param udId
     * @param manufacturer
     * @param model
     * @param api
     * @param size
     * @return void
     * @author ZhouYiXun
     * @des 发送安卓Info
     * @date 2021/8/16 19:59
     */
    public void androidInfo(String platform, String version, String udId, String manufacturer, String model, String api, String size) {
        sendStepLog(StepType.INFO, "",
                "设备操作系统：" + platform
                        + "<br>操作系统版本：" + version
                        + "<br>设备序列号：" + udId
                        + "<br>设备制造商：" + manufacturer
                        + "<br>设备型号：" + model
                        + "<br>安卓API等级：" + api
                        + "<br>设备分辨率：" + size
        );
    }
}