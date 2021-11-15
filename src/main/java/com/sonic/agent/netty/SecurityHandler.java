package com.sonic.agent.netty;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sonic.agent.tools.AgentTool;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger = LoggerFactory.getLogger(SecurityHandler.class);
    private NettyClient nettyClient;
    public static Channel channel = null;
    private String key;
    private String version;
    private String host;
    private int port;

    public SecurityHandler(NettyClient nettyClient, String key, String host, int port, String version) {
        this.nettyClient = nettyClient;
        this.key = key;
        this.host = host;
        this.port = port;
        this.version = version;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Agent:{} 请求连接到服务器 {} !", ctx.channel().localAddress(), ctx.channel().remoteAddress());
        JSONObject auth = new JSONObject();
        auth.put("msg", "auth");
        auth.put("key", key);
        ctx.channel().writeAndFlush(auth.toJSONString());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        JSONObject jsonMsg = JSON.parseObject((String) msg);
        logger.info("Agent:{} 收到服务器 {} 返回验证消息: {}", ctx.channel().localAddress(), ctx.channel().remoteAddress(), jsonMsg);
        if (jsonMsg.getString("msg") != null && jsonMsg.getString("msg").equals("auth") && jsonMsg.getString("result").equals("pass")) {
            logger.info("服务器认证通过！");
            logger.info("当前sonic-agent版本为：" + version);
            AgentTool.agentId = jsonMsg.getInteger("id");
            ctx.pipeline().remove(SecurityHandler.class);
            channel = ctx.channel();
            JSONObject agentInfo = new JSONObject();
            agentInfo.put("msg", "agentInfo");
            agentInfo.put("agentId", jsonMsg.getInteger("id"));
            agentInfo.put("port", port);
            agentInfo.put("version", version);
            agentInfo.put("systemType", System.getProperty("os.name"));
            agentInfo.put("host", host);
            channel.writeAndFlush(agentInfo.toJSONString());
            NettyThreadPool.readQueue();
            ctx.pipeline().addLast(new NettyClientHandler(nettyClient, channel));
        } else {
            logger.info("服务器认证不通过！");
            NettyThreadPool.isPassSecurity = false;
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.info("Agent: {} 发生异常 {}", ctx.channel().remoteAddress(), cause.getMessage());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Agent: {} 连接断开", ctx.channel().remoteAddress());
        NettyThreadPool.isPassSecurity = false;
        ctx.close();
    }
}