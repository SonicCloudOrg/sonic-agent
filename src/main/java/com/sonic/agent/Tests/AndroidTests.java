package com.sonic.agent.Tests;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.rabbitmq.client.Channel;
import com.sonic.agent.automation.AndroidStepHandler;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import com.sonic.agent.bridge.android.AndroidDeviceLocalStatus;
import com.sonic.agent.bridge.android.AndroidDeviceThreadPool;
import com.sonic.agent.cv.RecordHandler;
import com.sonic.agent.interfaces.DeviceStatus;
import com.sonic.agent.tools.MiniCapTool;
import org.bytedeco.javacv.FrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author ZhouYiXun
 * @des 安卓测试执行类
 * @date 2021/8/25 20:50
 */
@Component
public class AndroidTests {
    private final Logger logger = LoggerFactory.getLogger(AndroidTests.class);
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Value("${sonic.agent.key}")
    private String key;

    public void run(Channel channel, long tag, JSONObject jsonObject) throws IOException {
        AndroidStepHandler androidStepHandler = new AndroidStepHandler();
        List<JSONObject> steps = jsonObject.getJSONArray("steps").toJavaList(JSONObject.class);
        int rid = jsonObject.getInteger("rid");
        int cid = jsonObject.getInteger("cid");
        String udId = jsonObject.getJSONObject("device").getString("udId");
        JSONObject gp = jsonObject.getJSONObject("gp");
        androidStepHandler.setGlobalParams(gp);
        androidStepHandler.setTestMode(cid, rid, udId, DeviceStatus.TESTING, "");
        channel.basicAck(tag, true);
        int wait = jsonObject.getInteger("wait");
        if (!AndroidDeviceLocalStatus.startTest(udId)) {
            androidStepHandler.waitDevice(wait + 1);
            wait++;
            if (wait >= 12) {
                androidStepHandler.waitDeviceTimeOut(udId);
                androidStepHandler.sendStatus();
            } else {
                //延时队列
                logger.info("进入延时队列:" + jsonObject);
                jsonObject.put("wait", wait);
                rabbitTemplate.convertAndSend("TaskDirectExchange", key, jsonObject);
            }
            return;
        }
        //启动测试
        try {
            androidStepHandler.startAndroidDriver(udId);
        } catch (Exception e) {
            logger.error(e.getMessage());
            androidStepHandler.closeAndroidDriver();
            androidStepHandler.sendStatus();
            AndroidDeviceLocalStatus.finishError(udId);
            return;
        }

        //电量过低退出测试
        if (androidStepHandler.getBattery()) {
            androidStepHandler.closeAndroidDriver();
            androidStepHandler.sendStatus();
            AndroidDeviceLocalStatus.finish(udId);
            return;
        }

        //正常运行步骤的线程
        Future<?> runStep = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
            for (JSONObject step : steps) {
                try {
                    androidStepHandler.runStep(step);
                } catch (Throwable e) {
                    break;
                }
            }
        });

        //性能数据获取线程
        AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
            int tryTime = 0;
            while (!runStep.isDone()) {
                if (androidStepHandler.getAndroidDriver() == null) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        logger.error(e.getMessage());
                    }
                    continue;
                }
                try {
                    androidStepHandler.getPerform();
                    Thread.sleep(30000);
                } catch (Exception e) {
                    tryTime++;
                }
                if (tryTime > 10) {
                    break;
                }
            }
        });

        //录像线程
        Future<?> record = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
            Boolean isSupportRecord = true;
            String manufacturer = AndroidDeviceBridgeTool.getIDeviceByUdId(udId).getProperty(IDevice.PROP_DEVICE_MANUFACTURER);
            if (manufacturer.equals("HUAWEI") || manufacturer.equals("OPPO") || manufacturer.equals("vivo")) {
                isSupportRecord = false;
            }
            while (!runStep.isDone()) {
                if (androidStepHandler.getAndroidDriver() == null) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        logger.error(e.getMessage());
                    }
                    continue;
                }
//                File logDec = new File("test-output/log");
//                if (!logDec.exists()) {
//                    logDec.mkdirs();
//                }
                //写入logcat
//                File logcatFile = new File(logDec + File.separator + Calendar.getInstance().getTimeInMillis() + "_" + udId + ".log");
//                FileOutputStream logFileOut = null;
//                try {
//                    logFileOut = new FileOutputStream(logcatFile);
//                } catch (FileNotFoundException e) {
//                    logger.error(e.getMessage());
//                }
//                FileOutputStream finalLogFileOut = logFileOut;
                //添加监听
//                androidStepHandler.getAndroidDriver().addLogcatMessagesListener((msg) -> {
//                    try {
//                        finalLogFileOut.write((msg + "\n").getBytes(StandardCharsets.UTF_8));
//                    } catch (IOException e) {
//                        logger.error(e.getMessage());
//                    }
//                });
                //开始广播
//                androidStepHandler.getAndroidDriver().startLogcatBroadcast("localhost", AppiumServer.service.getUrl().getPort());
                Future<?> miniCapPro = null;
                AtomicReference<List<byte[]>> imgList = new AtomicReference<>(new ArrayList<>());
                AtomicReference<String[]> banner = new AtomicReference<>(new String[24]);
                if (isSupportRecord) {
                    try {
                        androidStepHandler.startRecord();
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                        isSupportRecord = false;
                    }
                } else {
                    MiniCapTool miniCapTool = new MiniCapTool();
                    miniCapPro = miniCapTool.start(udId, banner, imgList, "high", null);
                }
                //两分钟录一次
                try {
                    Thread.sleep(120000);
                } catch (InterruptedException e) {
                    logger.error(e.getMessage());
                }
                //移除监听
//                androidStepHandler.getAndroidDriver().removeAllLogcatListeners();
                //移除logcat广播
//                androidStepHandler.getAndroidDriver().stopLogcatBroadcast();
                //关闭流
//                if (logFileOut != null) {
//                    try {
//                        logFileOut.close();
//                    } catch (IOException e) {
//                        logger.error(e.getMessage());
//                    }
//                }
                //处理logcat日志
//                if (isFail.get()) {
//                    androidStepHandler.log.sendSelfLog(logcatFile.getName(), UploadTools.upload(logcatFile, "logFiles"));
//                } else {
//                    logcatFile.delete();
//                }
                //处理录像
                if (isSupportRecord) {
                    if (androidStepHandler.getStatus() == 3) {
                        androidStepHandler.stopRecord(udId);
                    } else {
                        androidStepHandler.getAndroidDriver().stopRecordingScreen();
                    }
                } else {
                    miniCapPro.cancel(true);
                    if (androidStepHandler.getStatus() == 3) {
                        File recordByRmvb = new File("test-output/record");
                        if (!recordByRmvb.exists()) {
                            recordByRmvb.mkdirs();
                        }
                        long timeMillis = Calendar.getInstance().getTimeInMillis();
                        String fileName = timeMillis + "_" + udId.substring(0, 4) + ".mp4";
                        File uploadFile = new File(recordByRmvb + File.separator + fileName);
                        try {
                            androidStepHandler.log.sendRecordLog(true, fileName,
                                    RecordHandler.record(uploadFile, imgList.get()
                                            , Integer.parseInt(banner.get()[9]), Integer.parseInt(banner.get()[13])));
                        } catch (FrameRecorder.Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        //等待两个线程结束了才结束方法
        while ((!record.isDone()) || (!runStep.isDone())) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }
        }
        androidStepHandler.closeAndroidDriver();
        androidStepHandler.sendStatus();
        AndroidDeviceLocalStatus.finish(udId);
    }
}
