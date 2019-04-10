package com.tubipa.bleandroid;

public class Constants {

    public static final String KEY_CONNECTED_ADDRESS = "KEY_CONNECTED_ADDRESS";

    public interface NOTIFICATION_ID {
        int FOREGROUND_SERVICE = 105;
        int FOREGROUND_DOWNLOAD_SERVICE = 106;
    }

    public interface ACTION {
        String MAIN_ACTION = "com.tubipa.bleandroid.action.main";
        String CONNECT_ACTION = "com.tubipa.bleandroid.action.connect";
        String DISCONNECT_ACTION = "com.tubipa.bleandroid.action.disconnect";
        String SCAN_ACTION = "com.tubipa.bleandroid.action.scan";
        String STOP_SCAN_ACTION = "com.tubipa.bleandroid.action.stopscan";
    }
}
