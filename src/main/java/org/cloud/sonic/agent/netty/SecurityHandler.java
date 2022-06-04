package org.cloud.sonic.agent.netty;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.cloud.sonic.agent.models.Cabinet;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.shc.SHCService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger = LoggerFactory.getLogger(SecurityHandler.class);
    private NettyClient nettyClient;
    public static Channel channel = null;
    private String agentKey;
    private String cabinetKey;
    private String version;
    private String host;
    private int port;
    private boolean cabinetEnable;
    private int storey;

    public SecurityHandler(NettyClient nettyClient, String agentKey, String host, int port, String version) {
        this.nettyClient = nettyClient;
        this.agentKey = agentKey;
        this.host = host;
        this.port = port;
        this.version = version;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Agent:{} 请求连接到服务器 {} !", ctx.channel().localAddress(), ctx.channel().remoteAddress());
        JSONObject auth = new JSONObject();
        auth.put("msg", "auth");
        auth.put("agentKey", agentKey);
        if (cabinetEnable) {
            auth.put("cabinetKey", cabinetKey);
        }
        ctx.channel().writeAndFlush(auth.toJSONString());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        JSONObject jsonMsg = JSON.parseObject((String) msg);
        logger.info("Agent:{} 收到服务器 {} 返回验证消息: {}", ctx.channel().localAddress(), ctx.channel().remoteAddress(), jsonMsg);
        if (jsonMsg.getString("msg") != null && jsonMsg.getString("msg").equals("auth") && jsonMsg.getString("result").equals("pass")) {
            logger.info("server auth successful!");
            logger.info("sonic-agent version: " + version);
            if (cabinetEnable) {
                if (jsonMsg.getString("cabinetAuth") != null && jsonMsg.getString("cabinetAuth").equals("pass")) {
                    SHCService.connect();
                    if (SHCService.status != SHCService.SHCStatus.OPEN) {
                        BytesTool.currentCabinet = JSON.parseObject("cabinet", Cabinet.class);
                        BytesTool.storey = storey;
                    } else {
                        logger.info("SHC连接失败！请确保您使用的是Sonic机柜，" +
                                "如果仍然连接不上，请在对应Agent所在主机执行[sudo service shc restart]");
                        NettyThreadPool.isPassSecurity = false;
                        ctx.close();
                    }
                } else {
                    logger.info("cabinet not found!");
                    NettyThreadPool.isPassSecurity = false;
                    ctx.close();
                }
            }
            BytesTool.agentId = jsonMsg.getInteger("id");
            ctx.pipeline().remove(SecurityHandler.class);
            channel = ctx.channel();
            JSONObject agentInfo = new JSONObject();
            agentInfo.put("msg", "agentInfo");
            agentInfo.put("agentId", jsonMsg.getInteger("id"));
            agentInfo.put("port", port);
            agentInfo.put("version", "v" + version);
            agentInfo.put("systemType", System.getProperty("os.name"));
            agentInfo.put("host", host);
            agentInfo.put("cabinetId", BytesTool.currentCabinet.getId());
            agentInfo.put("storey", BytesTool.storey);
            channel.writeAndFlush(agentInfo.toJSONString());
            NettyThreadPool.readQueue();
            ctx.pipeline().addLast(new NettyClientHandler(nettyClient, channel));
        } else {
            logger.info("server auth failed!");
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