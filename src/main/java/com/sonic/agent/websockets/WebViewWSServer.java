package com.sonic.agent.websockets;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/10/25 23:03
 */
@Component
@ServerEndpoint(value = "/websockets/webView", configurator = MyEndpointConfigure.class)
public class WebViewWSServer {
    InputStream inputStream = null;
    Map<Session, WebSocketClient> sessionWebSocketClientMap = new HashMap<>();

    @OnOpen
    public void onOpen(Session session) throws Exception {
//        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId("MDX0220610012002");
//        String webview = AndroidDeviceBridgeTool
//                .executeCommand(iDevice,"cat /proc/net/unix | grep webview");
//        String name = webview.substring(webview.indexOf("@"));
//        System.out.println(name);
//        AndroidDeviceBridgeTool.forward(iDevice, 8888, name);
        String url = "localhost:7778/devtools/page/E404897077123C962D30C19E350A23F4";
        CompletableFuture<String> awaitedResponse =  new CompletableFuture<>();
        URI uri = new URI(url);
        WebSocketClient webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {

            }

            @Override
            public void onMessage(String s) {
                if(awaitedResponse != null) {
                    awaitedResponse.complete(s);
                    awaitedResponse = null;
                } else {
                    // handle asynchronous message
                    System.out.println("Async message " + s);
                }
            }

            @Override
            public void onClose(int i, String s, boolean b) {

            }

            @Override
            public void onError(Exception e) {

            }
        };
        webSocketClient.connect();
        new Thread(()->{
                while (true){
                    awaitedResponse.get()
                }
        }).start();
        sessionWebSocketClientMap.put(session, webSocketClient);
    }

    @OnMessage
    public void onMessage(String message, Session session) throws InterruptedException {
        if (sessionWebSocketClientMap.get(session) != null) {
            try {
                sessionWebSocketClientMap.get(session).onMessage(message);
//                sessionWebSocketClientMap.get(session).send(message);
            } catch (Exception e) {

            }
        }
    }

    private void sendText(Session session, String message) {
        System.out.println(message);
        synchronized (session) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IllegalStateException | IOException e) {
//                logger.error("socket发送失败!连接已关闭！");
            }
        }
    }
}
