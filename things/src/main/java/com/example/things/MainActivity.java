package com.example.things;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 * <p>
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */
public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private Gpio mLedGpio;
    private ButtonInputDriver mButtonInputDriver;

    /* Remote LED Service UUID */
    public static UUID REMOTE_LED_SERVICE = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
    /* Remote LED Data Characteristic */
    public static UUID  REMOTE_LED_DATA =  UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb");
    //设备的名称，
    private static final String ANDROID_DEVICE_NAME = "Honor 9 Lite";
    private static final String ADAPTER_FRIENDLY_NAME = "My Android Things device";
    private static final int REQUEST_ENABLE_BT = 1;
    //Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;
   // 定义Service，与蓝牙模块进行通讯,在后台运行
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private String mDeviceAddress;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i(TAG,"Starting ButtonActivity");

        PeripheralManager pioManager =PeripheralManager.getInstance();

        Log.i(TAG,"Configuring GPIO pins");
        try {
            mLedGpio=pioManager.openGpio(BoardDefaults.getGPIOForLED());
            mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            Log.i(TAG,"Registering button driver");

            //初始化并注册将发出SPACE键事件的InputDriver,关于GPIO状态更改。
            mButtonInputDriver = new ButtonInputDriver(
                    BoardDefaults.getGPIOForButton(),
                    Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_SPACE);

            mHandler = new Handler();
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if(mBluetoothAdapter != null){
                if(mBluetoothAdapter.isEnabled()){
                    Log.d(TAG,"Bluetooth Adapter is already enabled.");
                    initScan();
                }else {
                    Log.e(TAG,"Bluetooth Adapter is already enabled.");
                    mBluetoothAdapter.enable();
                }
            }

        } catch (IOException e) {
         Log.e(TAG,"Error configuring GPIO pins",e);
        }
    }
    private void initScan(){
        if(mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()){
            Log.e(TAG,"Bluetooth adapter not available or not enabled.");
            return;
        }
        //setupBTProfiles();
        Log.d(TAG,"Set up Bluetooth Adapter name and profile");
        mBluetoothAdapter.setName(ANDROID_DEVICE_NAME);
        scanLeDevice();
    }

    //设置蓝牙配置文件并扫描附近的蓝牙设备
    private void scanLeDevice(){
        //在预定义的扫描周期后停止扫描。
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mScanning = false;
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        },SCAN_PERIOD);
        mScanning = true;
        mBluetoothAdapter.startLeScan(mLeScanCallback);
    }
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(BluetoothDevice device, int i, byte[] bytes) {
                    Boolean isDeviceFound = false;
                    if(device != null){
                        final String deviceName = device.getName();
                        if(deviceName != null && deviceName.length() > 0){
                            if(deviceName.equals(ANDROID_DEVICE_NAME)){
                                isDeviceFound = true;
                                mDeviceAddress = device.getAddress();
                            }
                        }
                    }
                    if(isDeviceFound && !mConnected){
                        Intent gattServiceIntent = new Intent(MainActivity.this,BluetoothLeService.class);
                        bindService(gattServiceIntent,mServiceConnection,BIND_AUTO_CREATE);
                        mConnected = true;
                    }
                }
            };
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_ENABLE_BT){
            Log.d(TAG,"Enable discoverable returned with result" +requestCode);
        }
        if(resultCode == RESULT_CANCELED){
            Log.e(TAG,"Enable discoverable has been cancelled by the user"
            +  "This should never happen in an Android Things device.");
        }
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService =((BluetoothLeService.LocalBinder)service).getService();

            if(!mBluetoothLeService.initialize()){
                Log.e(TAG,"Unable to initialize Bluetooth");
                finish();
            }
            Boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.e(TAG,"Unable to initialize Bluetooth");
            mConnected = true;
            if(mScanning){
                mScanning = false;
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService=null;
        }
    };

//处理服务触发的各种事件。
// ACTION_GATT_CONNECTED：连接到GATT服务器。
// ACTION_GATT_DISCONNECTED：与GATT服务器断开连接。
// ACTION_GATT_SERVICES_DISCOVERED：发现GATT服务。
// ACTION_DATA_AVAILABLE：收到来自设备的数据。 这可能是读取的结果
//或通知操作。

    /**
     * BroadcastReceiver 侦听来自连接的蓝牙服务器的消息
     * Android设备。当您从应用程序中按下切换按钮时，
     * 会发送一条消息，此接收器将捕获该消息并确定是否
     * 应打开或关闭LED
     */
private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                //在用户界面上显示所有支持的服务和特性。
                List<BluetoothGattService> service = mBluetoothLeService.getSupportedGattServices();
                if (service != null) {
                    for (BluetoothGattService gattService : service) {
                        if (gattService.getUuid().equals(REMOTE_LED_SERVICE)) {
                                final BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(REMOTE_LED_DATA);
                                if (characteristic != null) {
                                    final int charaProp = characteristic.getProperties();
                                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                                        //如果某个特征上存在活动通知，请清除,首先它不会更新用户界面上的数据字段。
                                        if (mNotifyCharacteristic != null) {
                                            mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, false);
                                            mNotifyCharacteristic = null;
                                        }
                                        mBluetoothLeService.readCharacteristic(characteristic);
                                    }
                                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                                        mNotifyCharacteristic = characteristic;
                                        mBluetoothLeService.setCharacteristicNotification(characteristic, true);
                                    }
                                }
                            }
                        }
                    }
                } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                    String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                    if (data.contains("false")) {
                        setLedValue(false);
                    } else if (data.contains("true")) {
                        setLedValue(true);
                    }
                }
            }
        };
private static IntentFilter makeGattUpdateIntentFilter(){
    final IntentFilter intentFilter =new  IntentFilter();
    intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
    intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
    intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
    intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
    return intentFilter;
   }

    @Override
    protected void onStart() {
        super.onStart();
        mButtonInputDriver.register();
        registerReceiver(mGattUpdateReceiver,makeGattUpdateIntentFilter());
    }
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if(keyCode == KeyEvent.KEYCODE_SPACE){
            //Turn on the LED
            setLedValue(true);
        }
        return super.onKeyDown(keyCode,event);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event){
        if(keyCode == KeyEvent.KEYCODE_SPACE){
            // Turn off the LED
            setLedValue(false);

        }
        return super.onKeyUp(keyCode,event);
    }

    /**
     * Update the value of the LED output.
     */
    private void  setLedValue(boolean value){
        try {
            mLedGpio.setValue(value);
        } catch (IOException e) {
              Log.e(TAG,"Error updating GPIO value",e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }
    /**
     * 不再使用服务器取消注册和解除绑定
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mButtonInputDriver != null) {
            mButtonInputDriver.unregister();

            try {
                mButtonInputDriver.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing Button driver", e);
            } finally {
                mButtonInputDriver = null;
            }
        }
        if(mLedGpio!=null){
            try {
                mLedGpio.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing LED GPIO", e);
            }finally {
                mLedGpio=null;
            }
            mLedGpio=null;
        }
        unbindService(mServiceConnection);
        mBluetoothLeService=null;
    }
}
