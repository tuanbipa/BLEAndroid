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
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BLEService extends Service {

    private static final String TAG = BLEService.class.getSimpleName();

    private static final String serviceCBUUID = "AB0828B1-198E-4351-B779-901FA0E0371E";
    private static final String characteristicCBUUID = "0972EF8C-7613-4075-AD52-756F33D4DA91";


    BluetoothManager bleManager;
    BluetoothAdapter bleAdapter;
    BluetoothLeScanner bleScanner;
    BluetoothGatt bluetoothGatt;
    String mBluetoothDeviceAddress;
    String mBluetoothDeviceName;
    boolean isReconnecting;
    boolean hasWaitingNotification;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        showNotification("BLEAndroid", "Scanning...", true);

        bleManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        bleAdapter = bleManager.getAdapter();
        bleScanner = bleAdapter.getBluetoothLeScanner();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            if (intent.getAction() != null) {
                if (intent.getAction().equals(Constants.ACTION.CONNECT_ACTION)) {

                    String address = intent.getStringExtra("address");
                    String name = intent.getStringExtra("name");

                    connect(address, name);
                }else if (intent.getAction().equals(Constants.ACTION.DISCONNECT_ACTION)){
                    disconnect();
                    close();
                }else if (intent.getAction().equals(Constants.ACTION.SCAN_ACTION)){
                    startScanning();
                }else if (intent.getAction().equals(Constants.ACTION.STOP_SCAN_ACTION)){
                    stopScan();
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        close();
    }

    public void disconnect() {

        SaveStore.saveString(Constants.KEY_CONNECTED_ADDRESS, null);

        EventBus.getDefault().post(new MainEvents.BLEConnection(0, mBluetoothDeviceAddress));

        if (!isReconnecting){
            showNotification("BLEAndroid", "Disconnected", true);
        }

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

    void stopScan(){
        showNotification("BLEAndroid", "Scan finished!", true);
        EventBus.getDefault().post(new MainEvents.BLEStopScanning());
        bleScanner.stopScan(leScanCallback);
    }

    void startScanning(){

        EventBus.getDefault().post(new MainEvents.BLEStartScanning());
        showNotification("BLEAndroid", "Scanning...", true);

        Log.d(TAG , "Scanning...");
        ScanFilter scanFilter = new ScanFilter.Builder().build();
        ScanSettings settings =new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).setReportDelay(0).build();

        bleScanner.startScan(Arrays.asList(scanFilter), settings, leScanCallback);
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "Found: Device Name: " + result.getDevice().getName() + "Device Address: " + result.getDevice().getAddress()  + " rssi: " + result.getRssi() + "\n");
            EventBus.getDefault().post(new MainEvents.BLEScanning(result.getDevice()));
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

    void connect(String address, String name){

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
            showNotification("BLEAndroid", "Device not found.  Unable to connect.", true);
            return;
        }

        Log.d(TAG, "Connecting to " + name);
        bluetoothGatt = device.connectGatt(this, false, mGattCallback);
        mBluetoothDeviceAddress = address;
        mBluetoothDeviceName = name;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isReconnecting = false;
                hasWaitingNotification = false;
                BluetoothDevice device = gatt.getDevice();
                if (device != null){
                    showNotification("BLEAndroid", "Connected to " + device.getName(), true);
                    EventBus.getDefault().post(new MainEvents.BLEConnection(1, device.getAddress()));

                    SaveStore.saveString(Constants.KEY_CONNECTED_ADDRESS, device.getAddress());
                }

                Log.i(TAG, "Connected to GATT server.");
                boolean success = bluetoothGatt.discoverServices();

                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" + success);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");

                String address = mBluetoothDeviceAddress;

                disconnect();
                close();

                //Keep connection
                //Reconnect
                isReconnecting = true;

                if (isReconnecting){
                    if (!hasWaitingNotification){
                        showNotification("BLEAndroid", "Waiting for device...", true);
                        hasWaitingNotification = true;
                    }
                }

                connect(address, mBluetoothDeviceName);

            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onServicesDiscovered received: " + status);

                List<BluetoothGattService> bluetoothGattServices = getSupportedGattServices();
                for (BluetoothGattService gattService : bluetoothGattServices){

                    if (gattService.getUuid().equals(UUID.fromString(serviceCBUUID))){
                        List<BluetoothGattCharacteristic> gattCharacteristics =
                                gattService.getCharacteristics();
                        // Loops through available Characteristics.
                        for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {

                            if (gattCharacteristic.getUuid().equals(UUID.fromString(characteristicCBUUID))){
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

            //Parse data
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                try {
                    String dataStr = new String(data, "UTF-8");
                    showNotification("BLEAndroid", "Changed: " + dataStr, true);

                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
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
        }

        showNotification("BLEAndroid", "Enable notifications", true);

        bluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (characteristicCBUUID.toLowerCase().equals(characteristic.getUuid().toString().toLowerCase())) {

            List<BluetoothGattDescriptor> bluetoothGattDescriptors =  characteristic.getDescriptors();
            for (BluetoothGattDescriptor descriptor : bluetoothGattDescriptors){
                Log.d(TAG, "descriptor: " + descriptor.getUuid());


                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                boolean success = bluetoothGatt.writeDescriptor(descriptor);
                Log.d(TAG, "Write descriptor " + success);

                showNotification("BLEAndroid", "Write descriptor " + success, true);
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
