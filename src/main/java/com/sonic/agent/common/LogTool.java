package com.sonic.agent.common;

import com.alibaba.fastjson.JSONObject;
import com.sonic.agent.maps.WebSocketSessionMap;
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
public class LogTool {
    private final Logger logger = LoggerFactory.getLogger(LogTool.class);
    public String socketSession = "";
    public DeviceStatus type;
    public int caseId = 0;
    public int resultId = 0;
    public String udId = "";

    /**
     * @param type
     * @return com.sonic.agent.common.LogTool
     * @author ZhouYiXun
     * @des 设置log类型，因为要区分发送到哪个地方
     * @date 2021/8/16 19:56
     */
    public LogTool setType(DeviceStatus type) {
        this.type = type;
        return this;
    }

    /**
     * @param message
     * @return void
     * @author ZhouYiXun
     * @des 判断发送到哪个地方
     * @date 2021/8/16 19:57
     */
    public void send(JSONObject message) {
        if (type.equals(DeviceStatus.DEBUGGING)) {
            sendToWebSocket(WebSocketSessionMap.getMap().get(socketSession), message);
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
        //发送方法
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
        log.put("udId", udId);
        log.put("cid", caseId);
        log.put("rid", resultId);
        log.put("udId", udId);
        send(log);
    }

    /**
     * @param erType
     * @param erMsg
     * @param erStack
     * @param type
     * @param ver
     * @param logName
     * @param url
     * @return void
     * @author ZhouYiXun
     * @des 发送崩溃与卡顿信息
     * @date 2021/8/16 19:58
     */
    public void sendCrash(String erType, String erMsg, String erStack, int type, String ver, String logName, String url) {
        JSONObject log = new JSONObject();
        log.put("msg", "crash");
        if (erType.length() == 0) {
            erType = "未知类型";
        }
        if (erMsg.length() == 0) {
            erMsg = "未知错误信息";
        }
        if (erStack.length() == 0) {
            erStack = "未知错误堆栈";
        }
        log.put("erType", erType.length() > 255 ? erType.substring(0, 240) + "..." : erType);
        log.put("erMsg", erMsg.length() > 255 ? erMsg.substring(0, 240) + "..." : erMsg);
        log.put("erStack", erStack.length() > 255 ? erStack.substring(0, 240) + "..." : erStack);
        log.put("ver", ver);
        log.put("type", type);
        log.put("log", logName);
        log.put("url", url);
        log.put("udId", udId);
        log.put("cid", caseId);
        log.put("rid", resultId);
        log.put("udId", udId);
        send(log);
    }

    /**
     * @param msgType
     * @param status
     * @param des
     * @param detail
     * @return void
     * @author ZhouYiXun
     * @des 发送普通步骤log
     * @date 2021/8/16 19:58
     */
    public void sendStepLog(String msgType, String status, String des, String detail) {
        JSONObject log = new JSONObject();
        log.put("msg", msgType);
        log.put("des", des);
        log.put("status", status);
        log.put("log", detail);
        log.put("cid", caseId);
        log.put("rid", resultId);
        log.put("udId", udId);
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
        sendStepLog("step", "info", "",
                "设备操作系统：" + platform + "<br>"
                        + "操作系统版本：" + version + "<br>"
                        + "设备序列号：" + udId + "<br>"
                        + "设备制造商：" + manufacturer + "<br>"
                        + "设备型号：" + model + "<br>"
                        + "安卓API等级：" + api + "<br>"
                        + "设备分辨率：" + size
        );
    }
}