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
package org.cloud.sonic.agent.tests;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.interfaces.DeviceStatus;
import org.cloud.sonic.agent.common.interfaces.StepType;
import org.cloud.sonic.agent.common.maps.WebSocketSessionMap;
import org.cloud.sonic.agent.transport.TransportWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.Session;
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
        logger.info(message.toJSONString());
        if (type.equals(DeviceStatus.DEBUGGING)) {
            sendToWebSocket(WebSocketSessionMap.getSession(sessionId), message);
        }
        if (type.equals(DeviceStatus.TESTING)) {
            sendToServer(message);
        }
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
        TransportWorker.send(message);
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
     * @param detail
     * @return void
     * @author ZhouYiXun
     * @des 发送性能数据
     * @date 2021/8/16 19:58
     */
    public void sendPerLog(String detail) {
        JSONObject log = new JSONObject();
        log.put("msg", "perform");
        log.put("des", "");
        log.put("status", 1);
        log.put("log", detail);
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
     * @param size
     * @return void
     * @author ZhouYiXun
     * @des 发送安卓Info
     * @date 2021/8/16 19:59
     */
    public void androidInfo(String platform, String version, String udId, String manufacturer, String model, String size) {
        sendStepLog(StepType.INFO, "",
                "设备操作系统：" + platform
                        + "<br>操作系统版本：" + version
                        + "<br>设备序列号：" + udId
                        + "<br>设备制造商：" + manufacturer
                        + "<br>设备型号：" + model
                        + "<br>设备分辨率：" + size
        );
    }
}
