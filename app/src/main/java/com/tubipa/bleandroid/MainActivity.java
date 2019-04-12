package com.tubipa.bleandroid;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.greenrobot.eventbus.EventBus;

public class MainActivity extends AppCompatActivity {

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

    EventBus bus = EventBus.getDefault();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Devices");

        mHandler = new Handler();

        listView = findViewById(R.id.listView);

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
    protected void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
        scan();
    }

    void stopScan(){
        mHandler.removeCallbacksAndMessages(null);
        mScanning = false;

        stopScanService();
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
}
