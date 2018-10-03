package com.busanbus.smartblock;

import com.busanbus.smartblock.model.UserData;
import com.busanbus.smartblock.model.UserDriveData;
import com.google.firebase.database.DatabaseReference;

public class UserRef {

    public static DatabaseReference userDataRef;
    public static UserData userData;
    public static DatabaseReference userDriveRef;
    public static UserDriveData userDriveData;
    public static boolean allowUserDrivingUpdate = false;

}
