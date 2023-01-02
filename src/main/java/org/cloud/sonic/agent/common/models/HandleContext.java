/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published
 *   by the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.common.models;

import org.cloud.sonic.driver.common.models.BaseElement;

import java.util.Iterator;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/8/26 23:46
 */
public class HandleContext {
    private String stepDes;
    private String detail;
    private Throwable e;

    public Iterator<BaseElement> iteratorElement;

    public BaseElement currentIteratorElement;

    public HandleContext() {
        this.stepDes = "";
        this.detail = "";
        this.e = null;
    }

    public void clear() {
        this.stepDes = "";
        this.detail = "";
        this.e = null;
    }

    public String getStepDes() {
        return stepDes;
    }

    public void setStepDes(String stepDes) {
        this.stepDes = stepDes;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Throwable getE() {
        return e;
    }

    public void setE(Throwable e) {
        this.e = e;
    }

    @Override
    public String toString() {
        return "HandleDes{" +
                "stepDes='" + stepDes + '\'' +
                ", detail='" + detail + '\'' +
                ", e=" + e +
                '}';
    }
}
