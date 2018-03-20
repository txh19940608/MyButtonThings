package com.example.mybuttonthings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.HashSet;
import java.util.Set;

import static android.os.Build.VERSION_CODES.O;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private BluetoothManager  mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    //中心，周边。周边是数据提供者，中心是数据接收/处理者。
    //这个是周边
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();
    boolean toggleLight = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        IntentFilter  filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
       // 动态注册,调用Context的registerReceiver（）方法
        registerReceiver(mBluetoothReceiver,filter);

        //未打开蓝牙，才需要打开蓝牙  ,调用系统API去打开蓝牙
        if(!mBluetoothManager.getAdapter().enable()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,0);
        }else{
            Log.d(TAG,"Bluetooth enabled...starting services");
            startAdvertising();
            startServer();
        }
        Button toggleButton = findViewById(R.id.toggle_button);
        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleLight = !toggleLight;
                notifyRegisteredDevices(toggleLight);
            }
        });
    }

    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
                    //写入接收广播后的操作
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.STATE_OFF);
                 switch (state){
                     case BluetoothAdapter.STATE_ON:
                         startAdvertising();
                         startServer();
                         break;
                     case BluetoothAdapter.STATE_OFF:
                         stopAdvertising();
                         stopServer();
                         break;
                     default:
                 }
        }
    };

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Remote LED Service.
     */
    private void startAdvertising(){
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        }
        if(mBluetoothLeAdvertiser == null){
            Log.w(TAG,"Failed to create advertiser");
            return;
        }
        //设置广播的模式,应该是跟功耗相关
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP
                ) {

            /**
             * 初始化蓝牙类
             * AdvertisingSettings.Builder 用于创建AdvertiseSettings
             * AdvertiseSettings中包含三种数据：AdvertiseMode, Advertise TxPowerLevel和AdvertiseType，其测试结果如下：
             * AdvertiseMode:
             * Advertise Mode                           Logcat频率                   检测到的频率
             * ADVERTISE_MODE_LOW_LATENCY          1/1600 milliseconds                1/1068 milliseconds
             * ADVERTISE_MODE_BALANCED             1/400 milliseconds                 1/295 milliseconds
             * ADVERTISE_MODE_LOW_POWER            1/160 milliseconds                 1/142 milliseconds
             */
            AdvertiseSettings  settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)  //设置广播的模式，低功耗，平衡和低延迟三种模式;
                    .setConnectable(true)  //设置是否可以连接
                    .setTimeout(0)             //广播的最长时间
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)  //设置广播的信号强度
                    .build();

          //广播内容的实例
            AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(true) //是否广播设备名称
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(new ParcelUuid(RemoteLedProfile.REMOTE_LED_SERVICE))  //添加服务进广播，即对外广播本设备拥有的服务。
                    .build();
            mBluetoothLeAdvertiser.startAdvertising(settings,data,mAdvertiseCallback);
        }
    }

    /**
     * Stop Bluetooth advertisements.
     */
    private void stopAdvertising(){
       if(mBluetoothLeAdvertiser == null)
           return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
        }
    }

    /**
     * 使用服务/特性初始化GATT服务器实例
     * 来自远程LED配置文件。
     *
     */
    private void startServer(){

        mBluetoothGattServer = mBluetoothManager.openGattServer(this,mGattServerCallback);
         if(mBluetoothGattServer == null){
             Log.w(TAG,"Unable to create GATT server");
             return;
         }
        mBluetoothGattServer.addService(RemoteLedProfile.createRemoteLedService());
    }

/**
 *关闭GATT服务器。
 */
    private void stopServer(){
        if(mBluetoothGattServer == null) return;
        mBluetoothGattServer.close();
    }

/**
 * Callback to receive information about the advertisement process.
 */
  private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
    @Override
    public void onStartSuccess(AdvertiseSettings settingsInEffect) {

        Log.i(TAG, "LE Advertise Started");
    }

    @Override
    public void onStartFailure(int errorCode) {
    Log.w(TAG,"LE Advertise Failed: " +errorCode);
    }
};

    /**
     * Send a remote led service notification to any devices that are subscribed
     * to the characteristic.
     */
    private void  notifyRegisteredDevices(Boolean toggle){

        if(mRegisteredDevices.isEmpty()){
            Log.i(TAG,"No subscribers registered");
            return;
        }
        Log.i(TAG,"Send update to " + mRegisteredDevices.size() +" subscribers");
        for (BluetoothDevice device : mRegisteredDevices){
            BluetoothGattCharacteristic ledDataCharacteristic = mBluetoothGattServer
                    .getService(RemoteLedProfile.REMOTE_LED_SERVICE)
                    .getCharacteristic(RemoteLedProfile.REMOTE_LED_DATA);
            ledDataCharacteristic.setValue(toggle.toString());
            mBluetoothGattServer.notifyCharacteristicChanged(device,ledDataCharacteristic,false);

        }
    }

    /**
     * 回调处理到GATT服务器的传入请求。
     * 所有对特征和描述符的读/写请求都在这里处理
     */
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {

            if(newState == BluetoothProfile.STATE_CONNECTED){
                Log.i(TAG, "BluetoothDevice CONNECTED: " + device);
                mRegisteredDevices.add(device);
                //把Profile理解为连接层或者应用层协
            }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                Log.i(TAG,"BluetoothDevice DISCONNECTED: " + device);
                //Remove device from any active subscriptions
                mRegisteredDevices.remove(device);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {

            long now = System.currentTimeMillis();
            if(RemoteLedProfile.REMOTE_LED_DATA.equals(characteristic.getUuid())){
                Log.i(TAG,"Read data");
                mBluetoothGattServer.sendResponse(device,requestId,
                        BluetoothGatt.GATT_SUCCESS,0,null);
            }else {
                // Invalid characteristic
                Log.w(TAG,"Invalid Characteristic Read: " +characteristic.getUuid());
                mBluetoothGattServer.sendResponse(device,requestId,BluetoothGatt.GATT_FAILURE,
                        0,null);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattDescriptor descriptor) {

            mBluetoothGattServer.sendResponse(device,requestId,BluetoothGatt.GATT_FAILURE,
                    0,null);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor, boolean preparedWrite,
                                             boolean responseNeeded, int offset, byte[] value) {

            if(responseNeeded){
                mBluetoothGattServer.sendResponse(device,requestId,
                        BluetoothGatt.GATT_FAILURE,0,null);
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if(bluetoothAdapter.isEnabled()){
            stopServer();
            stopAdvertising();
        }
        unregisterReceiver(mBluetoothReceiver);
    }
}
