package com.example.tiantiaf.pulse;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Debug;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class DeviceControlActivity extends FragmentActivity {

    private final static String TAG = DeviceControlActivity.class.getSimpleName();
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private TextView mUpdateTime;
    private TextView mDataOutput;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private Button Stone_Motor_Mode1_btn;
    private Button Stone_Motor_Off_btn;

    private long initDate = 0;

    private boolean    Stone_Motor_Status;
    private String      Stone_Motor_Btn_Txt;

    private boolean Stone_Motor_On      = true;
    private boolean Stone_Motor_Off     = false;

    private String Stone_Motor_Mode1_On_Txt    = "Sequence ON";
    private String Stone_Motor_Mode1_Off_Txt   = "Sequence OFF";

    private String Stone_Motor_On_Txt    = "Motor ON";
    private String Stone_Motor_Off_Txt   = "Motor OFF";

    private int sampleCounter = 0;
    private List<String[]> dataArray = new ArrayList<String[]>();

    private int[] dataTrans = {0x00, 0x01, 0x00, 0x00};

    private Spinner time_spinner;
    private Spinner pwm_spinner;

    private int time_value;
    private int pwm_value;

    /*This function is used to received the broadcast data from BluetoothLeService*/
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    //getActionBar().setTitle(R.string.Title_L_Connect_21);
                } else {
                    //getActionBar().setTitle(R.string.Title_Connect);
                }
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                //getActionBar().setTitle(R.string.Title_Disonnect);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                GetSettings();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                String[] dataStringArray = intent.getStringArrayExtra(BluetoothLeService.EXTRA_DATA);
                int length = dataStringArray.length;

                if (length == 4) { //if the broadcast length is 4, Characteristic for write is initialized

                    WriteChar(dataTrans);
                    Log.i("BLE", "WRITE CHAR");
                }
                if (length == 5) { //if the broadcast length is 5, Characteristic for setting is read
                    //FlashSettings(dataStringArray);
                }
                if (length == (mBluetoothLeService.MTUSize -3)) { //if the broadcast length is MTU-3, notification is received
                    //FlashData(dataStringArray);
                }
            }
        }
    };

    /*Send "Get settings requirement to BLE service*/
    private void GetSettings() {
        mBluetoothLeService.closeNotification(); // pause notification sending
        BluetoothGattCharacteristic temp = mBluetoothLeService.getSettings();
        if (temp != null) {
            mBluetoothLeService.readCharacteristic(temp);//init command to read the Characteristic
        }



    }

    /*Code to manage Service lifecycle.*/
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Debug.startMethodTracing("test");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        /* Code to decide Version */
        if (android.os.Build.VERSION.SDK_INT < 21) {
        }

        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        initDate = 0;

        //Reg the button in UI
        Stone_Motor_Mode1_btn = (Button) findViewById(R.id.Motor_Mode1_Btn);

        time_spinner = (Spinner) findViewById(R.id.time_spinner);
        pwm_spinner  = (Spinner) findViewById(R.id.pwm_spinner);

        String[] timeItems = getResources().getStringArray(R.array.time_select);
        String[] pwmItems = getResources().getStringArray(R.array.pwm_select);

        ArrayAdapter<String> timeAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, timeItems);
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        ArrayAdapter<String> pwmAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, pwmItems);
        pwmAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        time_spinner.setAdapter(timeAdapter);
        pwm_spinner.setAdapter(pwmAdapter);

        Stone_Motor_Off_btn = (Button) findViewById(R.id.Motor_Off_Btn);

        Stone_Motor_Status = false;
        Stone_Motor_Off_btn.setText(Stone_Motor_Off_Txt);

        if (android.os.Build.VERSION.SDK_INT < 21) {
            //Stone_Motor_Off_btn.setVisibility(View.INVISIBLE);
        }

        Stone_Motor_Mode1_btn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                /* Change the Motor status fisrt */
                dataTrans[1] =  1;
                dataTrans[2] = time_value;
                dataTrans[3] = pwm_value;

                Stone_Motor_Mode1_btn.setText(Stone_Motor_Mode1_On_Txt);

                WriteChar(dataTrans);

            }
        });

        Stone_Motor_Off_btn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {

                Stone_Motor_Status = (Stone_Motor_Status == true) ? false : true;

                /* Change the Motor status fisrt */
                dataTrans[0] = (Stone_Motor_Status == true ) ? 1 : 0;
                //dataTrans[0] = 1;
                dataTrans[1] = 0;
                dataTrans[2] = time_value;
                dataTrans[3] = pwm_value;

                if(Stone_Motor_Status == true)
                {
                    Stone_Motor_Off_btn.setText(Stone_Motor_On_Txt);
                    Stone_Motor_Mode1_btn.setVisibility(View.VISIBLE);
                    Stone_Motor_Mode1_btn.setEnabled(true);
                } else {
                    Stone_Motor_Off_btn.setText(Stone_Motor_Off_Txt);
                    Stone_Motor_Mode1_btn.setEnabled(false);
                    Stone_Motor_Mode1_btn.setVisibility(View.INVISIBLE);
                    Stone_Motor_Mode1_btn.setText(Stone_Motor_Mode1_Off_Txt);
                }


                WriteChar(dataTrans);

            }
        });

        time_value = 3;
        pwm_value = 55;

        time_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                time_value = position + 3;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        pwm_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                pwm_value = position * 2 + 50;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

    }

    private void sendChar () {
        BluetoothGattCharacteristic temp = mBluetoothLeService.getWriteChar();
        if (temp != null) {
            mBluetoothLeService.readCharacteristic(temp);
        } else {
            mDataOutput.setText("No Chara Find");
        }
    }

    /*This function is use to send the data out. Requires to call from mGattUpdateReceiver Success to read.*/
    private void WriteChar(int[] dataTrans)
    {
        BluetoothGattCharacteristic temp = mBluetoothLeService.getCTR_PULSECharacteristic();// get Characteristic from service
        if (temp != null) {

            byte[] dataTransByte = new byte[dataTrans.length];
            for (int i = 0; i < dataTrans.length; i++) {
                dataTransByte[i] = (byte) dataTrans[i];
            }
            temp.setValue(dataTransByte);
            temp.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);  //send out
            mBluetoothLeService.writeCharacteristic(temp);    //init command to write the Characteristic
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

}
