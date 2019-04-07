package com.tubipa.bleandroid;

import android.Manifest;
import android.app.AlertDialog;
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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.aware.Characteristics;
import android.os.AsyncTask;
import android.os.Build;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    //private static final UUID UUID_Service = UUID.fromString("19fc95c0-c111–11e3–9904–0002a5d5c51b");
    private static final ParcelUuid UID_SERVICE =
            ParcelUuid.fromString("F4FFFD0B-549C-4A3A-91CB-C5886BD972CD");

    private static final String heartRateServiceCBUUID = "F4FFFD0B-549C-4A3A-91CB-C5886BD972CD";
    private static final String bodySensorLocationCharacteristicCBUUID = "A10A608E-ECAC-4A9F-9BDC-9624DAC4C423";
    private static final String heartRateMeasurementCharacteristicCBUUID = "11F974B5-41E7-459B-A1E1-2A4906466A1D";
    private static final String CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = "0002902-0000-1000-8000-00805f9b34fb";
    BluetoothManager bleManager;
    BluetoothAdapter bleAdapter;
    BluetoothLeScanner bleScanner;
    BluetoothGatt bluetoothGatt;
    String mBluetoothDeviceAddress;

    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    Button btStartScan, btStopScan;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btStartScan = findViewById(R.id.btStartScan);
        btStartScan.setOnClickListener(this);

        bleManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        bleAdapter = bleManager.getAdapter();
        bleScanner = bleAdapter.getBluetoothLeScanner();

        if (bleAdapter != null && !bleAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
        }

        // Make sure we have access coarse location enabled, if not, prompt the user to enable it
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect peripherals.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                }
            });
            builder.show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    System.out.println("coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "Found: Device Name: " + result.getDevice().getName() + "Device Address: " + result.getDevice().getAddress()  + " rssi: " + result.getRssi() + "\n");

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
        bluetoothGatt = device.connectGatt(this, true, mGattCallback);
        mBluetoothDeviceAddress = address;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        bluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                String addres = mBluetoothDeviceAddress;
                disconnect();
                close();
                connect(addres);
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

    void startScanning(){

        Log.d(TAG , "Scanning...");
        ScanFilter scanFilter = new ScanFilter.Builder().setServiceUuid(UID_SERVICE).build();
        ScanSettings settings =new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED).setReportDelay(0).build();

        bleScanner.startScan(Arrays.asList(scanFilter), settings, leScanCallback);
    }

    void startService(){
        Intent service = new Intent(this, BLEService.class);
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startForegroundService(service);
        }else{
            startService(service);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btStartScan:
                //startScanning();
                startService();
                finish();
                break;
                default:break;
        }
    }
}
