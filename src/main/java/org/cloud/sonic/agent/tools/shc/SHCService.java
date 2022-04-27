/*
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.cloud.sonic.agent.tools.shc;

import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.common.maps.DevicesBatteryMap;

@Slf4j
public class SHCService {
    public static Integer getGear(String udId) {
        return DevicesBatteryMap.getGearMap().get(udId);
    }

    public static void setGear(String udId, int gear) {
        Integer currentGear = getGear(udId);
        if (currentGear == null || currentGear != gear) {
            //set
            log.info("Set!");
            DevicesBatteryMap.getGearMap().put(udId, gear);
        }
    }

    public static Integer getTemp(String udId){
        return DevicesBatteryMap.getTempMap().get(udId);
    }
}
