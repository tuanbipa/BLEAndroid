package com.tubipa.bleandroid;

public class Constants {
    public interface NOTIFICATION_ID {
        int FOREGROUND_SERVICE = 105;
        int FOREGROUND_DOWNLOAD_SERVICE = 106;
    }

    public interface ACTION {
        String MAIN_ACTION = "com.tubipa.bleandroid.action.main";
        String START_UDP_ACTION = "com.tubipa.bleandroid.action.START_UDP_ACTION";
        String START_MQTT_ACTION = "com.tubipa.bleandroid.action.START_MQTT_ACTION";
        String STOPFOREGROUND_ACTION = "com.tubipa.bleandroid.action.stop_foreground";
        String UPDATE_MESSAGE_NOTIFICATION_ACTION = "com.tubipa.bleandroid.action.update_message_notification";
        String UPDATE_CONNECTION_NOTIFICATION_ACTION = "com.tubipa.bleandroid.action.update_connection_notification";
    }
}
