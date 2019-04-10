package com.tubipa.bleandroid;

import android.bluetooth.BluetoothDevice;

public class MainEvents {
    public static class BLEConnection{
        private int status;
        String address;

        public BLEConnection(int status, String address) {
            this.status = status;
        }

        public String getAddress() {
            return address;
        }

        public int getStatus() {
            return status;
        }
    }

    public static class BLEScanning{
        BluetoothDevice device;

        public BLEScanning(BluetoothDevice device) {
            this.device = device;
        }

        public BluetoothDevice getDevice() {
            return device;
        }
    }

    public static class BLEStartScanning{
        public BLEStartScanning() {
        }
    }

    public static class BLEStopScanning{
        public BLEStopScanning() {
        }
    }
}
