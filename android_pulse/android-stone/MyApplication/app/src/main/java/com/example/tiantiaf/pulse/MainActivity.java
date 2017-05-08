package com.example.tiantiaf.pulse;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.tiantiaf.bluetooth.LeDeviceListAdapter;

public class MainActivity extends AppCompatActivity {

    private Button start_scan_Btn;
    private TextView bt_found_Tv;
    private Button start_read_Btn;

    private boolean mScanning;

    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;

    private BluetoothLeService mBluetoothLeService;
    private String mDeviceAddress;
    private boolean mConnected = false;

    private LeDeviceListAdapter mLeDeviceListAdapter;
    private static final long SCAN_PERIOD = 10000;

    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initGraphic();

    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        return super.onOptionsItemSelected(item);
    }

    private void initGraphic()
    {
        /* Init Graphic Interface */
        start_scan_Btn = (Button) findViewById(R.id.start_scan_btn);
        start_read_Btn = (Button) findViewById(R.id.start_read_btn);
        bt_found_Tv = (TextView) findViewById(R.id.bt_device_found);

        mLeDeviceListAdapter = new LeDeviceListAdapter();
        mHandler = new Handler();

        start_read_Btn.setVisibility(View.INVISIBLE);
        start_read_Btn.setEnabled(false);
        bt_found_Tv.setText("No BT Pulse Device Found!");

        start_scan_Btn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                /* Check whether device supports Bluetooth */
                if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                    Toast.makeText(getApplicationContext(), R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
                    finish();
                }

                /*  Initializes a Bluetooth adapter.
                    For API level 18 and above, get a reference to
                    BluetoothAdapter through BluetoothManager */
                final BluetoothManager bluetoothManager =
                        (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                mBluetoothAdapter = bluetoothManager.getAdapter();

                /* Check whether device supports Bluetooth */
                if (mBluetoothAdapter == null) {
                    Toast.makeText(getApplicationContext(), R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                if (!mBluetoothAdapter.isEnabled()) {
                    if (!mBluetoothAdapter.isEnabled()) {
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    }
                }
                scanLeDevice(true);

            }

        });

        start_read_Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readChar();
            }
        });

    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
        new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLeDeviceListAdapter.addDevice(device);

                    if(device.getName() != null)
                    {
                        if(device.getName().equals("BT_PULSE"))
                        {
                            mDeviceAddress = device.getAddress();
                            mBluetoothAdapter.stopLeScan(mLeScanCallback);

                            Intent gattServiceIntent = new Intent(getApplicationContext(), BluetoothLeService.class);
                            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

                            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

                        }

                        Log.d("DEVICE", "Device Name: " + device.getName() + "\n");
                    }

                }
            });
        }
    };

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e("Error", "Unable to initialize Bluetooth");
                finish();
            }
            /* Automatically connects to the device upon successful start-up initialization. */
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    /*This function is used to received the broadcast data from BluetoothLeService*/
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {

                mConnected = true;
                bt_found_Tv.setText("BT Pulse Device Found and Connected!");
                start_read_Btn.setVisibility(View.VISIBLE);
                start_read_Btn.setEnabled(true);

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                //readChar();
            } else if(BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)){
                byte[] data = intent.getByteArrayExtra(EXTRA_DATA);

                Log.d("DATA", "onCharacteristicRead: " + data[0] + "; " + data[1] + "; " + data[2]
                        + "; " + data[3]);
            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private void readChar () {
        BluetoothGattCharacteristic temp = mBluetoothLeService.getWriteChar();
        if (temp != null) {
            mBluetoothLeService.readCharacteristic(temp);


        }
    }


}
