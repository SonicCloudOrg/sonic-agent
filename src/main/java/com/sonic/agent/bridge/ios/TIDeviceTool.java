package com.sonic.agent.bridge.ios;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(value = "modules.ios.enable", havingValue = "true")
@DependsOn({"iOSThreadPoolInit", "nettyMsgInit"})
@Component
public class TIDeviceTool {


}
