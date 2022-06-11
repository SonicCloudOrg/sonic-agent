package org.cloud.sonic.agent.transport;

import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.tools.SpringTool;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * @author Eason
 * @date 2022/6/12 02:45
 */
@Slf4j
public class TransportConnectionThread implements Runnable {
    /**
     * second
     */
    public static final long DELAY = 10;

    public static final String THREAD_NAME = "transport-connection-thread";

    public static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    Boolean cabinetEnable = Boolean.valueOf(SpringTool.getPropertiesValue("sonic.agent.cabinet.enable"));
    String serverHost = String.valueOf(SpringTool.getPropertiesValue("sonic.server.host"));
    Integer serverPort = Integer.valueOf(SpringTool.getPropertiesValue("sonic.server.port"));
    String key = String.valueOf(SpringTool.getPropertiesValue("sonic.agent.key"));

    @Override
    public void run() {
        Thread.currentThread().setName(THREAD_NAME);
        if (TransportWorker.client == null && TransportWorker.isKeyAuth) {
            URI uri = URI.create(String.format("ws://%s:%d/websockets/agent/%s/1", serverHost, serverPort, key));
            TransportClient transportClient = new TransportClient(uri);
            transportClient.connect();
        }else{
            log.info("client health.");
        }
    }
}
