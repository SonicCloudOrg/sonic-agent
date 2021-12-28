package com.sonic.agent.netty;

import com.alibaba.fastjson.JSONObject;
import com.sonic.agent.tools.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MsgDispatcher extends Thread {
    private final Logger logger = LoggerFactory.getLogger(MsgDispatcher.class);
    /**
     * 设备信息的消息队列
     */
    private final BlockingQueue<JSONObject> mQueue;
    /**
     * 退出消息线程
     */
    private volatile boolean mQuit = false;

    public MsgDispatcher() {
        mQueue = new LinkedBlockingQueue<>();
    }

    public void send(JSONObject jsonObject) {
        if (jsonObject == null) {
            logger.error("jsonObject is null");
            return;
        }
        mQueue.add(jsonObject);
    }

    /**
     * 退出当前队列操作
     */
    public void quit() {
        mQuit = true;
        interrupt();
    }

    @Override
    public void run() {
        while (true) {
            try {
                processData();
            } catch (InterruptedException e) {
                //
                if (mQuit) {
                    Thread.currentThread().interrupt();
                    return;
                }

            }
        }
    }

    private void processData() throws InterruptedException {

        try {
            if (NettyClientHandler.channel != null) {
                JSONObject m = mQueue.take();
                m.put("agentId", AgentTool.agentId);
                if (NettyClientHandler.channel != null) { // 是否需要保证消息不丢失？
                    NettyClientHandler.channel.writeAndFlush(m.toJSONString());
                }else {
                    logger.warn("丢弃一个消息：{}", m);
                }
            } else {
                Thread.sleep(10000);
            }
        } catch (Throwable e) {
            logger.error("processData异常", e);
        }
    }
}
