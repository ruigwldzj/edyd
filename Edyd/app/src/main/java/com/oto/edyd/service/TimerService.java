package com.oto.edyd.service;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.location.LocationManagerProxy;
import com.amap.api.location.LocationProviderProxy;
import com.amap.api.maps.AMap;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.oto.edyd.utils.Constant;
import com.oto.edyd.utils.OkHttpClientManager;
import com.squareup.okhttp.Request;

import java.util.Timer;
import java.util.TimerTask;

/**
 * 定时获得经纬度
 * Created by yql on 2015/10/7.
 */
public class TimerService extends Service implements LocationSource, AMapLocationListener {

    private Timer timer; //定时对象
    private final int PERIOD = 30*1000;

    private AMap aMap;
    private MapView mapView;
    private OnLocationChangedListener mListener;
    private LocationManagerProxy mAMapLocationManager;
    private TimerServiceBinder binder = new TimerServiceBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        timer = new Timer();
        timer.schedule(new TimerGetLongitudeAndLatitude(), 0, PERIOD); //每隔十五秒执行一次
        mapView = new MapView(getApplicationContext());
        init();
    }
    /**
     * 初始化AMap对象
     */
    private void init() {
        if (aMap == null) {
            aMap = mapView.getMap();
            setUpMap();
        }
    }
    /**
     * 设置一些amap的属性
     */
    private void setUpMap() {
        aMap.setLocationSource(this);// 设置定位监听
        aMap.getUiSettings().setMyLocationButtonEnabled(true);// 设置默认定位按钮是否显示
        aMap.setMyLocationEnabled(true);// 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
        // 设置定位的类型为定位模式 ，可以由定位、跟随或地图根据面向方向旋转几种
        aMap.setMyLocationType(AMap.LOCATION_TYPE_LOCATE);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        timer.cancel();
        super.onDestroy();
    }

    /**
     * 定位成功后回调函数
     */
    @Override
    public void onLocationChanged(AMapLocation amapLocation) {
        if (mListener != null && amapLocation != null) {
            if (amapLocation != null && amapLocation.getAMapException().getErrorCode() == 0) {
                //sendLocationInfo(amapLocation); //发送定位类型
                deactivate();
            } else {
                Log.e("AmapErr","Location ERR:" + amapLocation.getAMapException().getErrorCode());
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    /**
     * 激活定位
     */
    @Override
    public void activate(OnLocationChangedListener listener) {
        mListener = listener;
        if (mAMapLocationManager == null) {
            mAMapLocationManager = LocationManagerProxy.getInstance(this);
            // 此方法为每隔固定时间会发起一次定位请求，为了减少电量消耗或网络流量消耗，
            // 注意设置合适的定位时间的间隔（最小间隔支持为2000ms），并且在合适时间调用removeUpdates()方法来取消定位请求
            // 在定位结束后，在合适的生命周期调用destroy()方法
            // 其中如果间隔时间为-1，则定位只定一次
            // 在单次定位情况下，定位无论成功与否，都无需调用removeUpdates()方法移除请求，定位sdk内部会移除
            mAMapLocationManager.requestLocationData(
                    LocationProviderProxy.AMapNetwork, -1, 10, this);
        }
    }

    /**
     * 再次激活定位
     * @param listener
     */
    public void reActivate(OnLocationChangedListener listener) {
        activate(listener);
    }

    /**
     * 停止定位
     */
    @Override
    public void deactivate() {
        //mListener = null;
        if (mAMapLocationManager != null) {
            mAMapLocationManager.removeUpdates(this);
            mAMapLocationManager.destroy();
        }
        mAMapLocationManager = null;
    }

    class TimerGetLongitudeAndLatitude extends TimerTask {
        @Override
        public void run() {
            Message message = new Message();
            message.what = 0x10;
            handler.sendMessage(message);
        }
    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if(msg.what == 0x10) {
                //执行定时操作
                reActivate(mListener);
            }
        }
    };

    public class TimerServiceBinder extends Binder {
        //返回本地服务
        public TimerService getService() {
            return TimerService.this;
        }
    }

    /**
     * 发送定位信息
     * @param amapLocation
     */
    private void sendLocationInfo(AMapLocation amapLocation) {
        String url = ""; //访问地址
        String accountId= ""; //登录用户ID
        String controlNum = ""; //调度单号
        String tel = ""; //电话号码
        String provider = amapLocation.getProvider(); //定位类型
        double longitude = amapLocation.getLongitude(); //经度
        double latitude = amapLocation.getLatitude(); //纬度
        float speed = 0f;
        float direction = 0f;
        if(provider.equals("lbs")) { //网格定位
            url = Constant.ENTRANCE_PREFIX + "appRecordTrackInfo.json?lng="+longitude+"&lat="+latitude+"&accountId="+accountId
                    +"&controlNum"+controlNum+"&tel"+tel;
        } else if(provider.equals("gps")) { //定位
            speed = amapLocation.getSpeed(); //速度
            direction = amapLocation.getBearing(); //定位方向
            url = Constant.ENTRANCE_PREFIX + "appRecordTrackInfo.json?lng="+longitude+"&lat="+latitude+"&accountId="+accountId+"&controlNum"
                    +controlNum+"&tel"+tel+"&speed="+speed+"&direction"+direction;
        }

        OkHttpClientManager.getAsyn(url, new OkHttpClientManager.ResultCallback<String>() {
            @Override
            public void onError(Request request, Exception e) {

            }

            @Override
            public void onResponse(String response) {

            }
        });
    }

    /**
     * 启动定时器
     */
    private void startTimer() {
        timer.schedule(new TimerGetLongitudeAndLatitude(), 0, PERIOD); //每隔十五秒执行一次
    }

    /**
     * 停止定时器
     */
    private void stopTimer() {
        timer.cancel();
    }
}
