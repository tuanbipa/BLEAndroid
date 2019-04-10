package com.tubipa.bleandroid;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;

public class LeDeviceListAdapter extends BaseAdapter {

    private ArrayList<BluetoothDevice> mLeDevices;
    private LayoutInflater mInflator;
    private Context mContext;

    public LeDeviceListAdapter(Context context) {
        super();
        mContext = context;
        mLeDevices = new ArrayList<BluetoothDevice>();
        mInflator = LayoutInflater.from(context);
    }

    public void addDevice(BluetoothDevice device) {
        if(!mLeDevices.contains(device)) {
            mLeDevices.add(device);
        }
    }

    public BluetoothDevice getDevice(int position) {
        return mLeDevices.get(position);
    }

    public void clear() {
        mLeDevices.clear();
    }

    @Override
    public int getCount() {
        return mLeDevices.size();
    }

    @Override
    public Object getItem(int i) {
        return mLeDevices.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder viewHolder;
        // General ListView optimization code.
        if (view == null) {
            view = mInflator.inflate(R.layout.device_item, null);
            viewHolder = new ViewHolder();
            viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
            viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
            viewHolder.btDisconnect = view.findViewById(R.id.btConnect);

            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        BluetoothDevice device = mLeDevices.get(i);
        String deviceName = device.getName();
        if (deviceName != null){
            deviceName = deviceName.trim();
        }
        if (!TextUtils.isEmpty(deviceName))
            viewHolder.deviceName.setText(deviceName);
        else
            viewHolder.deviceName.setText("N/A");

        viewHolder.deviceAddress.setText(device.getAddress());

        viewHolder.btDisconnect.setVisibility(View.GONE);

        String adddress = SaveStore.getString(Constants.KEY_CONNECTED_ADDRESS, null);
        if (!TextUtils.isEmpty(adddress)){
            if (adddress.contentEquals(device.getAddress())){
                viewHolder.btDisconnect.setVisibility(View.VISIBLE);
            }
        }

        viewHolder.btDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent service = new Intent(mContext, BLEService.class);
                service.setAction(Constants.ACTION.DISCONNECT_ACTION);

                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    mContext.startForegroundService(service);
                }else{
                    mContext.startService(service);
                }
            }
        });

        return view;
    }

    class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        Button btDisconnect;
    }

}
