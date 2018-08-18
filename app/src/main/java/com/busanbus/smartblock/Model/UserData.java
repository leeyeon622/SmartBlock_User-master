package com.busanbus.smartblock.model;

public class UserData {

    private String phone;
    private String company;
    private String time_login;
    private String state_frequency;
    private String state_ble;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getTime_login() {
        return time_login;
    }

    public void setTime_login(String time_login) {
        this.time_login = time_login;
    }

    public String getState_frequency() {
        return state_frequency;
    }

    public void setState_frequency(String state_frequency) {
        this.state_frequency = state_frequency;
    }

    public String getState_ble() {
        return state_ble;
    }

    public void setState_ble(String state_ble) {
        this.state_ble = state_ble;
    }
}
