package com.example.tiantiaf.sensor;

import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

/**
 * Created by tiantiaf on 2017/5/8.
 */

public class BlePPGSensor extends BleSensor<int[]>{

    private final static String TAG = BlePPGSensor.class.getSimpleName();
    private static final String UUID_SENSOR_BODY_LOCATION = "0000CBB1-0000-1000-8000-00805F9B34FB";

    private int location = -1;

    BlePPGSensor() {
        super();
    }

    @Override
    public String getName() {
        return "PPG";
    }

    @Override
    public String getServiceUUID() {
        return "0000180d-0000-1000-8000-00805f9b34fb";
    }

    public static String getServiceUUIDString() {
        return "0000180d-0000-1000-8000-00805f9b34fb";
    }

    @Override
    public String getDataUUID() {
        return "00002a37-0000-1000-8000-00805f9b34fb";
    }

    public static String getDataUUIDString() {
        return "00002a37-0000-1000-8000-00805f9b34fb";
    }

    @Override
    public String getConfigUUID() {
        return "00002902-0000-1000-8000-00805f9b34fb";
    }

    @Override
    public String getCharacteristicName(String uuid) {
        if (UUID_SENSOR_BODY_LOCATION.equals(uuid))
            return getName() + " Sensor body location";
        return super.getCharacteristicName(uuid);
    }

    @Override
    public boolean onCharacteristicRead(BluetoothGattCharacteristic c) {
        super.onCharacteristicRead(c);

        Log.d(TAG, "onCharacteristicsReas");

        if ( !c.getUuid().toString().equals(UUID_SENSOR_BODY_LOCATION) )
            return false;

        location = c.getProperties();
        Log.d(TAG, "Sensor body location: " + location);
        return true;
    }

    @Override
    public String getDataString() {
        final int[] data = getData();
        return "heart rate=" + data[0] + "\ninterval=" + data[1];
    }

    @Override
    public int[] parse(BluetoothGattCharacteristic c) {

        int[] result = null;

        return result;
    }





}
