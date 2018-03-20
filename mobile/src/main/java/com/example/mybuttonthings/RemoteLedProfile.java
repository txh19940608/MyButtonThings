package com.example.mybuttonthings;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import java.util.UUID;

public class RemoteLedProfile {

    /* Remote LED Service UUID */
    public static UUID REMOTE_LED_SERVICE = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
    /* Remote LED Data Characteristic */
    public static UUID  REMOTE_LED_DATA =  UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb");


    /**
     * 获取服务与特征
     * 这个是中心,是数据接收/处理者
     * @return
     */
    public static BluetoothGattService createRemoteLedService(){
        //a.获取服务
        BluetoothGattService service = new BluetoothGattService(REMOTE_LED_DATA,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        //b.获取特征
        BluetoothGattCharacteristic ledData = new BluetoothGattCharacteristic(REMOTE_LED_DATA,
                //只读特性，支持通知
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);

        service.addCharacteristic(ledData);
                return service;
    }
}