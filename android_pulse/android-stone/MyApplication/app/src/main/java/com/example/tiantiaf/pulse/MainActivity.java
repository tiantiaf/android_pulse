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
import android.graphics.Color;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Paint.Align;

import com.example.tiantiaf.bluetooth.LeDeviceListAdapter;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;


public class MainActivity extends AppCompatActivity {

    private Button start_scan_Btn;
    private Button get_setting_Btn;
    private Button start_read_Btn;
    private Button connect_Btn;

    private TextView bt_found_Tv;

    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;

    private BluetoothLeService mBluetoothLeService;
    private String mDeviceAddress;
    private boolean mConnected = false;
    private boolean isStartReading = false;
    private boolean mScanning = false;
    private boolean mDiscovered = false;

    private LeDeviceListAdapter mLeDeviceListAdapter;
    private static final long SCAN_PERIOD = 10000;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING   = 1;
    private static final int STATE_CONNECTED    = 2;

    /* Sensor Frame Index */
    private static final int HEADER_INDEX       = 0;
    private static final int TIME_FIRST_INDEX   = 1;
    private static final int TIME_SECOND_INDEX  = 2;
    private static final int TIME_THIRD_INDEX   = 3;
    private static final int TIME_FORTH_INDEX   = 4;

    private static final int SENSOR1_LOW_INDEX  = 5;
    private static final int SENSOR1_HIGH_INDEX = 6;
    private static final int SENSOR2_LOW_INDEX  = 7;
    private static final int SENSOR2_HIGH_INDEX = 8;
    private static final int SENSOR3_LOW_INDEX  = 9;
    private static final int SENSOR3_HIGH_INDEX = 10;
    private static final int SENSOR4_LOW_INDEX  = 11;
    private static final int SENSOR4_HIGH_INDEX = 12;
    private static final int SENSOR5_LOW_INDEX  = 13;
    private static final int SENSOR5_HIGH_INDEX = 14;

    private static final int FOOTER_INDEX        = 15;

    private static final int sensorPerPacket     = 5;

    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static int StreamingChar = 0;
    public final static int SettingSyncChar = 1;

    private XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
    private XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
    private XYSeries series = new XYSeries("BLUE", 0);
    private GraphicalView graphicalView;
    private int numberOfSample = 5;

    private XYMultipleSeriesRenderer hrvRenderer = new XYMultipleSeriesRenderer();
    private XYMultipleSeriesDataset hrvDataset = new XYMultipleSeriesDataset();
    private XYSeries hrvSeries = new XYSeries("BLUE", 0);
    private GraphicalView hrvGraphicalView;
    private int numberOfHRVSampel = 30;

    private long timeFromStart;
    private int timeStart = 0;
    private int[] sensorData = new int[5];
    private int[] filterData = new int[3];

    private HRV_Processing hrv_processing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUI();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mConnected)
        {
            if(mDiscovered) {
                mBluetoothLeService.setCharacteristicNotification(mBluetoothLeService.getCharacteristic(StreamingChar), false);            }
            mBluetoothLeService.disconnect();
        }
        unbindService(mServiceConnection);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        if(!mScanning) {
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_stop).setVisible(false);
        } else {
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_stop).setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
            case R.id.menu_exit:
                this.finish();
                break;
        }

        return super.onOptionsItemSelected(item);
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

                            connect_Btn.setBackgroundColor(Color.rgb(63, 81, 181));
                            connect_Btn.setEnabled(true);

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

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                start_read_Btn.setBackgroundColor(Color.GRAY);
                start_read_Btn.setEnabled(false);

                get_setting_Btn.setBackgroundColor(Color.GRAY);
                get_setting_Btn.setEnabled(false);

                bt_found_Tv.setText("BT Pulse Device Disconnected!");

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {

                mDiscovered = true;
                mBluetoothLeService.closeNotification();
                bt_found_Tv.setText("BT Pulse Device Found and Connected!");

                start_read_Btn.setBackgroundColor(Color.rgb(63, 81, 181));
                start_read_Btn.setVisibility(View.VISIBLE);
                start_read_Btn.setEnabled(true);
                start_read_Btn.setText("Start Read");

                get_setting_Btn.setBackgroundColor(Color.rgb(63, 81, 181));
                get_setting_Btn.setVisibility(View.VISIBLE);
                get_setting_Btn.setEnabled(true);

                connect_Btn.setText("Disconnect Pulse Device!");

            } else if(BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)){
                String[] dataStringArray = intent.getStringArrayExtra(BluetoothLeService.EXTRA_DATA);

                FlashData(dataStringArray);

            } else if(BluetoothLeService.ACTION_SETTINGS_AVAILABLE.equals(action)) {
                byte[] data = intent.getByteArrayExtra(EXTRA_DATA);

                Log.d("DATA", "onCharacteristicRead: " + data[0] + "; " + data[1] + "; " + data[2]
                        + "; " + data[3]);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                start_scan_Btn.setBackgroundColor(Color.rgb(63, 81, 181));
                start_scan_Btn.setEnabled(true);
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
        BluetoothGattCharacteristic temp = mBluetoothLeService.getCharacteristic(SettingSyncChar);
        if (temp != null) {
            mBluetoothLeService.readCharacteristic(temp);
        }
    }

    private void pulseDataChar () {
        BluetoothGattCharacteristic temp = mBluetoothLeService.getCharacteristic(StreamingChar);
        if (temp != null) {
            mBluetoothLeService.readCharacteristic(temp);
        }
    }

    private void initUI()
    {
        /* Init Graphic Interface */
        get_setting_Btn = (Button) findViewById(R.id.get_setting_btn);
        start_read_Btn  = (Button) findViewById(R.id.start_read_btn);
        connect_Btn     = (Button) findViewById(R.id.connect_btn);

        bt_found_Tv = (TextView) findViewById(R.id.bt_device_found);

        mLeDeviceListAdapter = new LeDeviceListAdapter();
        mHandler = new Handler();

        start_read_Btn.setBackgroundColor(Color.GRAY);
        start_read_Btn.setEnabled(false);

        get_setting_Btn.setBackgroundColor(Color.GRAY);
        get_setting_Btn.setEnabled(false);

        connect_Btn.setBackgroundColor(Color.GRAY);
        connect_Btn.setEnabled(false);

        bt_found_Tv.setText("No BT Pulse Device Found!");

        connect_Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mConnected) {
                    Intent gattServiceIntent = new Intent(getApplicationContext(), BluetoothLeService.class);
                    bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

                    registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

                } else {
                    if (mBluetoothLeService.mConnectionState == STATE_CONNECTED)
                    {
                        mBluetoothLeService.setCharacteristicNotification(mBluetoothLeService.getCharacteristic(StreamingChar), false);
                        mBluetoothLeService.disconnect();
                    }

                    unbindService(mServiceConnection);
                    connect_Btn.setText("Connect to Pulse Device!");
                }
            }
        });

        get_setting_Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readChar();
            }
        });

        start_read_Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isStartReading)
                {
                    /* Disable Reading */
                    start_read_Btn.setText("Start Read");
                    mBluetoothLeService.setCharacteristicNotification(mBluetoothLeService.getCharacteristic(StreamingChar), false);
                } else {
                    /* Enable Reading */
                    start_read_Btn.setText("Stop Read");
                    mBluetoothLeService.setCharacteristicNotification(mBluetoothLeService.getCharacteristic(StreamingChar), true);
                }

                isStartReading = (isStartReading == true) ? false : true;
            }
        });

        initBluetooth();
        initPulseGraph();
        initHRVGraph();

        hrv_processing = new HRV_Processing();
        hrv_processing.initParameters();

    }

    private void initBluetooth() {
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
        //scanLeDevice(true);
    }

    private void initPulseGraph() {
        renderer.setAxisTitleTextSize(16);
        renderer.setChartTitleTextSize(20);
        renderer.setLabelsTextSize(15);
        renderer.setPointSize(5f);
        renderer.setMargins(new int[]{20, 30, 15, 20});
        renderer.setShowLegend(false);

        XYSeriesRenderer r = new XYSeriesRenderer();
        r.setColor(Color.RED);
        r.setLineWidth(3f);
        renderer.addSeriesRenderer(r);

        int length = renderer.getSeriesRendererCount();
        for (int i = 0; i < length; i++) {
            ((XYSeriesRenderer) renderer.getSeriesRendererAt(i))
                    .setFillPoints(true);
        }

        renderer.setXLabelsColor(Color.BLACK);
        renderer.setYLabelsColor(0, Color.BLACK);
        renderer.setYAxisMin(1000);
        renderer.setYAxisMax(1080);
        renderer.setAxesColor(Color.BLACK);
        renderer.setLabelsColor(Color.BLACK);
        renderer.setMarginsColor(Color.WHITE);
        renderer.setXLabels(11);
        renderer.setXTitle("seconds");
        renderer.setYLabels(10);
        renderer.setShowGrid(true);
        renderer.setXLabelsAlign(Align.RIGHT);
        renderer.setYLabelsAlign(Align.RIGHT);
        renderer.setZoomButtonsVisible(false);
        renderer.setZoomEnabled(false, false);
        dataset = new XYMultipleSeriesDataset();
        series = new XYSeries("");
        renderer.setXAxisMin(0);
        renderer.setXAxisMax(5);
        renderer.setSelectableBuffer(50);
        renderer.setXAxisMax(numberOfSample);
        renderer.setInScroll(false);
        renderer.setPanEnabled(false);
        series.add(0.00,0);

        dataset.addSeries(series);
        LinearLayout layout = (LinearLayout) findViewById(R.id.dataChart);
        graphicalView = ChartFactory.getLineChartView(this, dataset, renderer);
        layout.addView(graphicalView);
    }

    private void initHRVGraph() {
        hrvRenderer.setAxisTitleTextSize(16);
        hrvRenderer.setChartTitleTextSize(20);
        hrvRenderer.setLabelsTextSize(15);
        hrvRenderer.setPointSize(5f);
        hrvRenderer.setMargins(new int[]{20, 30, 15, 20});
        hrvRenderer.setShowLegend(false);

        XYSeriesRenderer r = new XYSeriesRenderer();
        r.setColor(Color.RED);
        r.setLineWidth(3f);
        hrvRenderer.addSeriesRenderer(r);

        int length = hrvRenderer.getSeriesRendererCount();
        for (int i = 0; i < length; i++) {
            ((XYSeriesRenderer) hrvRenderer.getSeriesRendererAt(i))
                    .setFillPoints(true);
        }

        hrvRenderer.setXLabelsColor(Color.BLACK);
        hrvRenderer.setYLabelsColor(0, Color.BLACK);
        hrvRenderer.setYAxisMin(600);
        hrvRenderer.setYAxisMax(1000);
        hrvRenderer.setAxesColor(Color.BLACK);
        hrvRenderer.setLabelsColor(Color.BLACK);
        hrvRenderer.setMarginsColor(Color.WHITE);
        hrvRenderer.setXLabels(11);
        hrvRenderer.setXTitle("seconds");
        hrvRenderer.setYLabels(10);
        hrvRenderer.setShowGrid(true);
        hrvRenderer.setXLabelsAlign(Align.RIGHT);
        hrvRenderer.setYLabelsAlign(Align.RIGHT);
        hrvRenderer.setZoomButtonsVisible(false);
        hrvRenderer.setZoomEnabled(false, false);
        hrvDataset = new XYMultipleSeriesDataset();
        hrvSeries = new XYSeries("");
        hrvRenderer.setXAxisMin(0);
        hrvRenderer.setSelectableBuffer(50);
        hrvRenderer.setXAxisMax(numberOfHRVSampel);
        hrvRenderer.setInScroll(false);
        hrvRenderer.setPanEnabled(false);
        hrvSeries.add(0.00,0);

        hrvDataset.addSeries(hrvSeries);
        LinearLayout layout = (LinearLayout) findViewById(R.id.hrvChart);
        hrvGraphicalView = ChartFactory.getLineChartView(this, hrvDataset, hrvRenderer);
        layout.addView(hrvGraphicalView);
    }

    private void FlashData(String[] dataStringArray) {
        int[] dataReceived_DEC = new int[dataStringArray.length];
        try {
            for (int i = 0; i < dataStringArray.length; i++) {
                dataReceived_DEC[i] = Integer.parseInt(dataStringArray[i], 16);
            }// process the data from GattCallback
        } catch (Exception ex) {
            Log.d("Packet_Data", ex.toString());
            for (int i = 0; i < dataStringArray.length; i++) {
                dataReceived_DEC[i] = 0;
                Log.d("Packet_Data", i + " " + dataStringArray[i]);
            }
        }
        //Log.d("Packet_Data", "onCharacteristicRead: " + dataReceived_DEC[0] + "; " + dataReceived_DEC[1]
                //+ "; "+ dataReceived_DEC[2] + "; " + dataReceived_DEC[5] + "; " + dataReceived_DEC[6]);

        int timeCurrent = dataReceived_DEC[TIME_FIRST_INDEX] + (dataReceived_DEC[TIME_SECOND_INDEX] << 8)
                + (dataReceived_DEC[TIME_THIRD_INDEX] << 16) + (dataReceived_DEC[TIME_FORTH_INDEX] << 24);
        if (timeStart == 0) {
            timeStart = timeCurrent;
        }
        timeFromStart = timeCurrent - timeStart;

        for (int i = 0; i < sensorPerPacket; i++) {
            sensorData[i] = dataReceived_DEC[SENSOR1_LOW_INDEX + (i * 2)]
                    + (dataReceived_DEC[SENSOR1_HIGH_INDEX + (i * 2)] << 8);

            int filterSensorData = filterData(sensorData[i]);
            Log.d("Sensor Data", "Sensor Data: " + filterSensorData);
            series.add((double)(timeFromStart + 20 * i)/1000.00, filterSensorData);

            hrv_processing.setSignal(filterSensorData, timeFromStart + 20 * i);

            if (hrv_processing.calcRRInterval()) {
                hrv_processing.setPulseFind(false);
                hrvSeries.add((double)(timeFromStart + 20 * i)/1000.00,
                        hrv_processing.getIBI() );
            }
        }

        updatePulseGraph();

    }

    private void updatePulseGraph() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                double timeX = (double) timeFromStart / 1000.0;
                if(timeX > numberOfSample) {
                    renderer.setXAxisMin(timeX - numberOfSample);
                    renderer.setXAxisMax(timeX);
                }
                if (timeX > numberOfHRVSampel) {
                    hrvRenderer.setXAxisMin(timeX - numberOfHRVSampel);
                    hrvRenderer.setXAxisMax(timeX);
                }
                graphicalView.repaint();
                hrvGraphicalView.repaint();
            }
        });

    }

    private int filterData(int sensorData) {
        int i = 0, filterSensorData;
        for (i = 0; i < 2; i++) {
            filterData[i] = filterData[i + 1];
        }
        filterData[2] = sensorData;

        filterSensorData = (filterData[0] + filterData[1] + filterData[2]) / 3;

        return filterSensorData;
    }

}
