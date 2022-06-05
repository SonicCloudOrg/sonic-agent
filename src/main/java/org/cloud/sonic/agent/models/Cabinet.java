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
package org.cloud.sonic.agent.models;

import lombok.Data;

import java.io.Serializable;

@Data
public class Cabinet implements Serializable {
    private Integer id;
    private Integer size;
    private String name;
    private String secretKey;
    private Integer lowLevel;
    private Integer lowGear;
    private Integer highLevel;
    private Integer highGear;
    private Integer highTemp;
    private Integer highTempTime;
    private String robotSecret;
    private String robotToken;
    private Integer robotType;
}
