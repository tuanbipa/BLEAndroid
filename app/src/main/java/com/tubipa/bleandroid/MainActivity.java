package com.tubipa.bleandroid;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import org.greenrobot.eventbus.EventBus;
import java.util.ArrayList;
import java.util.List;
import androidx.annotation.NonNull;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;

public class MainActivity extends AppCompatActivity{

    private static final String TAG = MainActivity.class.getSimpleName();

    private Handler mHandler;
    private static final long SCAN_PERIOD = 6000;

    BluetoothManager bleManager;
    BluetoothAdapter bleAdapter;
    BluetoothLeScanner bleScanner;

    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private boolean mScanning;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Devices");
        mHandler = new Handler();

        listView = findViewById(R.id.listView);

        EventBus.getDefault().register(this);

        bleManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        bleAdapter = bleManager.getAdapter();
        bleScanner = bleAdapter.getBluetoothLeScanner();

        if (bleAdapter != null && !bleAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
        }


        mLeDeviceListAdapter = new LeDeviceListAdapter(this);
        listView.setAdapter(mLeDeviceListAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
                if (device == null) return;

                if (mScanning) {
                    stopScan();
                }
                connect(device.getAddress(), device.getName());
            }
        });

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
        }else{

            if (bleAdapter != null && bleAdapter.isEnabled()) {
                mLeDeviceListAdapter.clear();
                startScanning();
            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @org.greenrobot.eventbus.Subscribe
    public void onConnectivityWifiChange(final MainEvents.BLEConnection event) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int status = event.getStatus();
                mLeDeviceListAdapter.notifyDataSetChanged();
            }
        });
    }

    @org.greenrobot.eventbus.Subscribe
    public void onScanningChange(final MainEvents.BLEScanning event){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                BluetoothDevice device = event.getDevice();

                mLeDeviceListAdapter.addDevice(device);
                mLeDeviceListAdapter.notifyDataSetChanged();
            }
        });
    }

    @org.greenrobot.eventbus.Subscribe
    public void onStartScanningChange(final MainEvents.BLEStartScanning event){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

            }
        });
    }

    @org.greenrobot.eventbus.Subscribe
    public void onStopScanningChange(final MainEvents.BLEStopScanning event){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                startScanning();
                break;
            case R.id.menu_stop:
                stopScan();
                break;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    System.out.println("coarse location permission granted");
                    if (bleAdapter != null && bleAdapter.isEnabled()) {
                        mLeDeviceListAdapter.clear();
                        startScanning();
                    }
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

    void startScanning(){
        // Stops scanning after a pre-defined scan period.
        mHandler.removeCallbacksAndMessages(null);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mScanning = false;
                stopScan();
                invalidateOptionsMenu();
            }
        }, SCAN_PERIOD);

        mScanning = true;
        invalidateOptionsMenu();

        Log.d(TAG , "Scanning...");

//        BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
//        no.nordicsemi.android.support.v18.scanner.ScanSettings settings = new no.nordicsemi.android.support.v18.scanner.ScanSettings.Builder()
//                .setLegacy(false)
//                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
//                .setUseHardwareBatchingIfSupported(true)
//                .build();
//        List<no.nordicsemi.android.support.v18.scanner.ScanFilter> filters = new ArrayList<>();
//        filters.add(new no.nordicsemi.android.support.v18.scanner.ScanFilter.Builder().build());
//        scanner.startScan(filters, settings, scanCallback);

        scan();
    }

    void stopScan(){
        mHandler.removeCallbacksAndMessages(null);
        mScanning = false;

        stopScanService();
//        BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
//        scanner.stopScan(scanCallback);

        invalidateOptionsMenu();
    }

    void connect(String address, String name){

        Intent service = new Intent(this, BLEService.class);
        service.setAction(Constants.ACTION.CONNECT_ACTION);
        service.putExtra("address", address);
        service.putExtra("name", name);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startForegroundService(service);
        }else{
            startService(service);
        }
    }

    void scan(){
        Intent service = new Intent(this, BLEService.class);
        service.setAction(Constants.ACTION.SCAN_ACTION);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startForegroundService(service);
        }else{
            startService(service);
        }
    }

    void stopScanService(){
        Intent service = new Intent(this, BLEService.class);
        service.setAction(Constants.ACTION.STOP_SCAN_ACTION);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startForegroundService(service);
        }else{
            startService(service);
        }
    }

    private no.nordicsemi.android.support.v18.scanner.ScanCallback scanCallback = new no.nordicsemi.android.support.v18.scanner.ScanCallback(){

        @Override
        public void onScanResult(int callbackType, @NonNull no.nordicsemi.android.support.v18.scanner.ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.d(TAG, "Found: Device Name: " + result.getDevice().getName() + "Device Address: " + result.getDevice().getAddress() +
                    " rssi: " + result.getRssi() +
                    "\n");
            mLeDeviceListAdapter.addDevice(result.getDevice());
            mLeDeviceListAdapter.notifyDataSetChanged();
        }

        @Override
        public void onBatchScanResults(@NonNull List<no.nordicsemi.android.support.v18.scanner.ScanResult> results) {
            super.onBatchScanResults(results);
            Log.d(TAG, "result: " + results);
            for (final no.nordicsemi.android.support.v18.scanner.ScanResult scanResult : results){
                Log.d(TAG, "Found: Device Name: " + scanResult.getDevice().getName() + "Device Address: " + scanResult.getDevice().getAddress() +
                        " rssi: " + scanResult.getRssi() +
                        "\n");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mLeDeviceListAdapter.addDevice(scanResult.getDevice());
                        mLeDeviceListAdapter.notifyDataSetChanged();
                    }
                });
            }
            stopScan();
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            stopScan();
        }
    };

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {

            Log.d(TAG, "Found: Device Name: " + result.getDevice().getName() + "Device Address: " + result.getDevice().getAddress() +
                    " rssi: " + result.getRssi() +
                    "\n");

            ParcelUuid[] uuids = result.getDevice().getUuids();
            if (uuids != null) {
                for (ParcelUuid uuid : result.getDevice().getUuids()) {
                    Log.d(TAG, "uuid: " + uuid.toString());
                }
            }
        }
    };
}
