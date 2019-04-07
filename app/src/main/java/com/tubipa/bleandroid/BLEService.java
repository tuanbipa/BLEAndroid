package com.tubipa.bleandroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BLEService extends Service {

    private static final String TAG = BLEService.class.getSimpleName();

    private static final String heartRateServiceCBUUID = "ab0828b1-198e-4351-b779-901fa0e0371e";
    private static final String heartRateMeasurementCharacteristicCBUUID = "0972EF8C-7613-4075-AD52-756F33D4DA91";
    private static final String CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = "0002902-0000-1000-8000-00805f9b34fb";
    private static final String bodySensorLocationCharacteristicCBUUID = "A10A608E-ECAC-4A9F-9BDC-9624DAC4C423";

//    private static final String heartRateServiceCBUUID = "F4FFFD0B-549C-4A3A-91CB-C5886BD972CD";
//    private static final String bodySensorLocationCharacteristicCBUUID = "A10A608E-ECAC-4A9F-9BDC-9624DAC4C423";
//    private static final String heartRateMeasurementCharacteristicCBUUID = "11F974B5-41E7-459B-A1E1-2A4906466A1D";
//    private static final String CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = "0002902-0000-1000-8000-00805f9b34fb";

    private static final ParcelUuid UID_SERVICE =
            ParcelUuid.fromString(heartRateServiceCBUUID);

    BluetoothManager bleManager;
    BluetoothAdapter bleAdapter;
    BluetoothLeScanner bleScanner;
    BluetoothGatt bluetoothGatt;
    String mBluetoothDeviceAddress;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        bleManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        bleAdapter = bleManager.getAdapter();
        bleScanner = bleAdapter.getBluetoothLeScanner();

        startScanning();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        close();
    }

    public void disconnect() {
        if (bleAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        bluetoothGatt.disconnect();
    }

    public void close() {
        if (bluetoothGatt == null) {
            return;
        }
        bluetoothGatt.close();
        bluetoothGatt = null;
        mBluetoothDeviceAddress = null;
    }

    void startScanning(){
        showNotification("BLEAndroid", "Scanning...", true);

        Log.d(TAG , "Scanning...");
        ScanFilter scanFilter = new ScanFilter.Builder().setServiceUuid(UID_SERVICE).build();
        ScanSettings settings =new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED).setReportDelay(0).build();

        bleScanner.startScan(Arrays.asList(scanFilter), settings, leScanCallback);
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "Found: Device Name: " + result.getDevice().getName() + "Device Address: " + result.getDevice().getAddress()  + " rssi: " + result.getRssi() + "\n");

            showNotification("BLEAndroid", "Found: Device Name: " + result.getDevice().getName(), true);

            //Stop scan if found
            bleScanner.stopScan(leScanCallback);
            Log.d(TAG , "Stopped");

            connect(result.getDevice().getAddress());
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d(TAG, "onScanFailed " + errorCode);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);

            Log.d(TAG, "onBatchScanResults: " + results.toString());
        }
    };

    void connect(String address){

        if (TextUtils.isEmpty(address)){
            Log.d(TAG, "Can not connect, address is null");
            return;
        }

        //Try to reconnect
        if (!TextUtils.isEmpty(mBluetoothDeviceAddress) && address.contentEquals(mBluetoothDeviceAddress)){
            Log.d(TAG, "ReConnecting to " + address);
            if (bluetoothGatt.connect()){
                return;
            }
        }

        BluetoothDevice device = bleAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.d(TAG, "Device not found.  Unable to connect.");
            return;
        }

        Log.d(TAG, "Connecting to " + address);
        bluetoothGatt = device.connectGatt(this, false, mGattCallback);
        mBluetoothDeviceAddress = address;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        bluetoothGatt.discoverServices());

                showNotification("BLEAndroid", "Connected to GATT server.", true);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");

                String address = mBluetoothDeviceAddress;
                disconnect();
                close();
                connect(address);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onServicesDiscovered received: " + status);

                List<BluetoothGattService> bluetoothGattServices = getSupportedGattServices();
                for (BluetoothGattService gattService : bluetoothGattServices){

                    if (gattService.getUuid().equals(UUID.fromString(heartRateServiceCBUUID))){
                        List<BluetoothGattCharacteristic> gattCharacteristics =
                                gattService.getCharacteristics();
                        // Loops through available Characteristics.
                        for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {

                            if (gattCharacteristic.getUuid().equals(UUID.fromString(bodySensorLocationCharacteristicCBUUID))){
                                //readCharacteristic(gattCharacteristic);
                            }

                            if (gattCharacteristic.getUuid().equals(UUID.fromString(heartRateMeasurementCharacteristicCBUUID))){
                                setCharacteristicNotification(gattCharacteristic, true);
                            }
                        }

                    }
                }


            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, characteristic.toString());

            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                Log.d(TAG, "Changed: " + " " + stringBuilder.toString());

                showNotification("BLEAndroid", "Changed: " + " " + stringBuilder.toString(), true);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                final byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    final StringBuilder stringBuilder = new StringBuilder(data.length);
                    for(byte byteChar : data)
                        stringBuilder.append(String.format("%02X ", byteChar));
                    Log.d(TAG, "Read: " + " " + stringBuilder.toString());
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }


    };

    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (bleAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        if (characteristic.getProperties() != BluetoothGattCharacteristic.PROPERTY_NOTIFY){
            return;
        }//00002901-0000-1000-8000-00805f9b34fb 00002902-0000-1000-8000-00805f9b34fb

        bluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (heartRateMeasurementCharacteristicCBUUID.toLowerCase().equals(characteristic.getUuid().toString().toLowerCase())) {

            List<BluetoothGattDescriptor> bluetoothGattDescriptors =  characteristic.getDescriptors();
            for (BluetoothGattDescriptor descriptor : bluetoothGattDescriptors){
                Log.d(TAG, "descriptor: " + descriptor.getUuid());
//                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//                boolean success = bluetoothGatt.writeDescriptor(descriptor);
//                Log.d(TAG, "Write descriptor " + success);
            }

            BluetoothGattDescriptor bluetoothGattDescriptor = characteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID));
            if (bluetoothGattDescriptor != null){
                bluetoothGattDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                boolean success = bluetoothGatt.writeDescriptor(bluetoothGattDescriptor);
                Log.d(TAG, "Write descriptor " + success);
            }
        }
    }


    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (bleAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        bluetoothGatt.readCharacteristic(characteristic);
    }


    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (bluetoothGatt == null) return null;

        return bluetoothGatt.getServices();
    }

    private void showNotification(String title, String content, boolean hasSound) {

        Log.i(TAG, content);

        String chanelID = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            chanelID = createNotificationChannel();
        }
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Constants.ACTION.MAIN_ACTION);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);


        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, chanelID)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        Uri soundUri = null;
        if (hasSound){
            //Define sound URI
            soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            mBuilder.setSound(soundUri);

        }
        Notification notification = mBuilder.build();

        startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE,
                notification);

    }

    String createNotificationChannel() {
        String channelId = "ble_channel_id";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);


            CharSequence channelName = "BLE Tracker";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel notificationChannel = new NotificationChannel(channelId, channelName, importance);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.enableVibration(true);
            notificationChannel.setShowBadge(true);
            notificationChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
            notificationManager.createNotificationChannel(notificationChannel);
        }
        return channelId;
    }
}
