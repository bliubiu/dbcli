package com.dbcli.model;

public class DatabaseNode {
    private String host;
    private Integer port;
    private String svcName;   // 对应服务名（Service Name）
    private String sidName;   // 对应 SID
    private String role;      // master / standby

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getSvcName() {
        return svcName;
    }

    public void setSvcName(String svcName) {
        this.svcName = svcName;
    }

    public String getSidName() {
        return sidName;
    }

    public void setSidName(String sidName) {
        this.sidName = sidName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}