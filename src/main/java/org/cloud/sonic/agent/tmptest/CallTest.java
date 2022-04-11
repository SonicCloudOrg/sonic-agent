package org.cloud.sonic.agent.tmptest;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.cloud.sonic.common.feign.ControllerFeignClient;
import org.cloud.sonic.common.http.RespModel;
import org.cloud.sonic.common.models.domain.Devices;
import org.cloud.sonic.common.services.DevicesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author JayWenStar
 * @date 2022/4/5 1:41 下午
 */
@Component
@Slf4j
public class CallTest implements ApplicationRunner {

    @DubboReference private DevicesService devicesService;

    @Autowired private ControllerFeignClient controllerFeignClient;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("RPC 测试，查找所有Android设备");
        List<Devices> allAndroid = devicesService.findAll(1);
        log.info("RPC Android设备列表：{}", allAndroid);

        log.info("openfeign 测试，查找所有Android设备");
        // RespModel<List<Devices>> respModel = SpringTool.getBean(ControllerFeignClient.class).listAll(1);
        RespModel<List<Devices>> respModel = controllerFeignClient.listAll(1);
        if (respModel.getCode() != 2000) {
            throw new Exception("openfeign调度失败，返回信息：" + respModel.toString());
        }
        log.info("openfeign Android设备列表：{}", respModel);
    }
}
