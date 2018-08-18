package com.busanbus.smartblock.model;

public class UserDriveData {

    private String phone;
    private String time_start;
    private String time_logout;
    private String state_drive;


    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getTime_start() {
        return time_start;
    }

    public void setTime_start(String time_start) {
        this.time_start = time_start;
    }

    public String getTime_logout() {
        return time_logout;
    }

    public void setTime_logout(String time_logout) {
        this.time_logout = time_logout;
    }

    public String getState_drive() {
        return state_drive;
    }

    public void setState_drive(String state_drive) {
        this.state_drive = state_drive;
    }
}
