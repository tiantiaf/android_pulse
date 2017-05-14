package com.example.tiantiaf.pulse;

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
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.UUID;

public class BluetoothLeService extends Service {

    private final static String TAG = BluetoothLeService.class.getSimpleName();
    public int MTUSize               = 23;

    private BluetoothGattCharacteristic CTR_PULSE_SETTING_Characteristic;
    private BluetoothGattCharacteristic CTR_PULSE_DATA_Characteristic;
    private BluetoothManager            mBluetoothManager;
    private BluetoothAdapter            mBluetoothAdapter;
    private String                      mBluetoothDeviceAddress;
    private BluetoothGatt               mBluetoothGatt;
    public int mConnectionState                       = STATE_DISCONNECTED;
    private static final int STATE_DISCONNECTED     = 0;
    private static final int STATE_CONNECTING       = 1;
    private static final int STATE_CONNECTED        = 2;

    private  int sensor=0;
    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_SETTINGS_AVAILABLE=
            "com.example.bluetooth.le.ACTION_SETTINGS_AVAILABLE";

    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    public final static String EXTRA_LENGTH=
            "com.example.bluetooth.le.EXTRA_LENGTH";
    public final static UUID BLEServiceUUID =
            UUID.fromString(SampleGattAttributes.BLEService);
    public final static UUID  CTR_PULSE_SETTING_UUID =
            UUID.fromString(SampleGattAttributes.CTR_PULSE);
    public final static UUID  CTR_PULSE_DATA_UUID =
            UUID.fromString(SampleGattAttributes.CTR_PULSE_DATA);
    public final static UUID  NotificationUUID =
            UUID.fromString(SampleGattAttributes.Notification);

    public final static int StreamingChar = 0;
    public final static int SettingSyncChar = 1;

    /* Connection State Change Callback */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(BLEServiceUUID);
                CTR_PULSE_SETTING_Characteristic = service.getCharacteristic(CTR_PULSE_SETTING_UUID);
                CTR_PULSE_DATA_Characteristic = service.getCharacteristic(CTR_PULSE_DATA_UUID);

                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        /*This is function is used to get notification feedback, when notification received it will send data
        to broadcast data processing function */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            broadcastUpdate(ACTION_DATA_AVAILABLE, data, data.length);
        }

        /*This is function is used to read characteristic, when read success, this function will be called.
        Hence it works with "readCharacteristic" in activity
        when data received it will send data to broadcast data processing function */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                byte[] data = characteristic.getValue();

                if (characteristic.getUuid().equals(CTR_PULSE_SETTING_UUID)) {
                    broadcastUpdate(ACTION_SETTINGS_AVAILABLE, data, data.length);

                    Log.d(TAG, "Pulse Setting Read: " + data[0] + "; " + data[1] + "; " + data[2]
                            + "; " + data[3]);

                } else if (characteristic.getUuid().equals(CTR_PULSE_DATA_UUID)) {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, data, data.length);

                    Log.d(TAG, "Pulse Data Read: " + data[0] + "; " + data[1] + "; " + data[2]
                            + "; " + data[3]);
                }


            }
        }

        /*This is function is called when writing success (Tare), after that we need to update sensor settings
        so, directly call read function to read 8051 settings */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mBluetoothGatt.readCharacteristic(CTR_PULSE_SETTING_Characteristic);
            }
        }

        @Override
        public boolean equals(Object o) {
            return super.equals(o);
        }

    };

    /*This function is used to send background service data to activity*/
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    public BluetoothLeService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /*Initializes a reference to the local Bluetooth adapter.*/
    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /* Connects to the GATT server hosted on the Bluetooth LE device.*/
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);

        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /* Disconnects an existing connection or cancel a pending connection.*/
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /*After using a given BLE device, the app must call this method to ensure resources are released properly.*/
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /*Read the characteristic value by given characteristic, after success, value will be send to onCharacteristicRead*/
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /*Write the characteristic value by given characteristic, after success, value will be send to onCharacteristicWrite*/
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /* Enables or disables notification on a give characteristic.This function will also change the characteristic's descriptor value */
    public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                                 boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        BluetoothGattDescriptor descriptor = CTR_PULSE_DATA_Characteristic.getDescriptor(NotificationUUID);
        try {
            if (enabled) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            } else {
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            }
            return mBluetoothGatt.writeDescriptor(descriptor);
        }
        catch (Exception e) {
            Log.e(TAG, e + "");
        }
        return false;
    }

    /*Process the data received from BLE characteristics and send it to activity*/
    private void broadcastUpdate(final String action,
                                 final byte[] data, final int length) {
        final Intent intent = new Intent(action);
        if (data != null && data.length > 0) {

            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            String dataString = new String(data) + "" + stringBuilder.toString();
            String dataStringProcessed = dataString.substring(length, dataString.length());
            String[] dataStringArray = dataStringProcessed.split("\\s+");
            intent.putExtra(EXTRA_DATA, dataStringArray);
             Log.d("BroadcastUpdate",action);
            sendBroadcast(intent);

            //intent.putExtra(EXTRA_DATA, data);
            //sendBroadcast(intent);
        }
    }

    /*close notification*/
    public void closeNotification()
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(CTR_PULSE_DATA_Characteristic, false);
    }

    /*Change to the HIGH speed mode, larger power consumption and ONLY USED IN ANDROID 5.0+*/
    public void setPriorityHIGH()
    {
        if ( android.os.Build.VERSION.SDK_INT >= 21) {
            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        }
    }

    /*Change to the NORMAL speed mode, balanced power consumption and ONLY USED IN ANDROID 5.0+*/
    public void setPriorityBALANCED()
    {
        if ( android.os.Build.VERSION.SDK_INT >= 21) {
            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
        }
    }

    /*Get the BLE to Tablet characteristic*/
    public BluetoothGattCharacteristic getCharacteristic(int characteristic){
        switch(characteristic){
            case SettingSyncChar:
                return CTR_PULSE_SETTING_Characteristic;
            case StreamingChar:
                return CTR_PULSE_DATA_Characteristic;

            default:
                return null;
        }
    }

}
