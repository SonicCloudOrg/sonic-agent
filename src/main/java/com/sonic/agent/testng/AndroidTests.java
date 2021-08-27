package com.sonic.agent.testng;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.sonic.agent.automation.AndroidStepHandler;
import com.sonic.agent.automation.AppiumServer;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import com.sonic.agent.bridge.android.AndroidDeviceLocalStatus;
import com.sonic.agent.bridge.android.AndroidDeviceThreadPool;
import com.sonic.agent.cv.RecordHandler;
import com.sonic.agent.interfaces.DeviceStatus;
import com.sonic.agent.interfaces.ResultDetailStatus;
import com.sonic.agent.maps.AndroidDeviceManagerMap;
import com.sonic.agent.tools.MiniCapTool;
import com.sonic.agent.tools.UploadTools;
import org.bytedeco.javacv.FrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author ZhouYiXun
 * @des 安卓测试执行类
 * @date 2021/8/25 20:50
 */
public class AndroidTests {
    private final Logger logger = LoggerFactory.getLogger(AndroidTests.class);

    @DataProvider(name = "testData", parallel = true)
    public Object[][] getTestData(ITestContext context) {
        int rid = Integer.parseInt(context.getCurrentXmlTest().getParameter("rid"));
        String dataInfo = context.getCurrentXmlTest().getParameter("dataInfo");
        JSONObject globalParams = JSON.parseObject(context.getCurrentXmlTest().getParameter("gp"));
        List<String> udIdList = JSON.parseArray(
                context.getCurrentXmlTest().getParameter("udIdList")).toJavaList(String.class);
        List<JSONObject> dataProvider = new ArrayList<>();
        for (String udId : udIdList) {
            if (AndroidDeviceManagerMap.getMap().get(udId) != null
                    || !AndroidDeviceBridgeTool.getIDeviceByUdId(udId).getState()
                    .equals(IDevice.DeviceState.ONLINE)) {
                continue;
            }
            JSONObject deviceTestData = new JSONObject();
            deviceTestData.put("udId", udId);
            deviceTestData.put("rid", rid);
            deviceTestData.put("dataInfo", dataInfo);
            deviceTestData.put("gp", globalParams);
            dataProvider.add(deviceTestData);
        }
        Object[][] testDataProvider = new Object[dataProvider.size()][];
        for (int i = 0; i < dataProvider.size(); i++) {
            testDataProvider[i] = new Object[]{dataProvider.get(i)};
        }
        return testDataProvider;
    }

    @Test(dataProvider = "testData", description = "Android端测试")
    public void run(JSONObject dataProvider) throws InterruptedException {
        AndroidStepHandler androidStepHandler = new AndroidStepHandler();
        JSONObject jsonObject = JSON.parseObject(dataProvider.getString("dataInfo"));
        int rid = dataProvider.getInteger("rid");
        int cid = jsonObject.getJSONObject("case").getInteger("id");
        String udId = dataProvider.getString("udId");
        JSONObject gp = dataProvider.getJSONObject("gp");
        androidStepHandler.setGlobalParams(gp);
        androidStepHandler.setTestMode(cid, rid, udId, DeviceStatus.TESTING);
        AndroidDeviceLocalStatus.startTest(udId);

        //启动测试
        try {
            androidStepHandler.startAndroidDriver(udId);
        } catch (Exception e) {
            AndroidDeviceLocalStatus.finishError(udId);
            androidStepHandler.closeAndroidDriver();
            logger.error(e.getMessage());
            throw e;
        }

        //电量过低退出测试
        if (androidStepHandler.getBattery()) {
            AndroidDeviceLocalStatus.finish(udId);
            androidStepHandler.closeAndroidDriver();
            return;
        }

        //正常运行步骤的线程
        Future<?> runStep = AndroidDeviceThreadPool.cachedThreadPool.submit(() -> {
            JSONArray steps = jsonObject.getJSONArray("steps");
            for (Object step : steps) {
                JSONObject stepDetail = (JSONObject) step;
                try {
                    androidStepHandler.runStep(stepDetail);
                } catch (Throwable e) {
                    androidStepHandler.errorScreen();
                    androidStepHandler.exceptionLog(e);
                    androidStepHandler.setResultDetailStatus(ResultDetailStatus.FAIL);
                    return;
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
                AtomicBoolean isFail = new AtomicBoolean(false);
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
                    miniCapPro = miniCapTool.start(udId, banner, imgList);
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
                    if (isFail.get()) {
                        androidStepHandler.stopRecord(udId);
                    } else {
                        androidStepHandler.getAndroidDriver().stopRecordingScreen();
                    }
                } else {
                    miniCapPro.cancel(true);
                    if (isFail.get()) {
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
        AndroidDeviceLocalStatus.finish(udId);
    }
}
