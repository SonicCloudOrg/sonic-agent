package org.cloud.sonic.agent.models;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/8/26 23:46
 */
public class HandleDes {
    private String stepDes;
    private String detail;
    private Throwable e;

    public HandleDes(){
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
