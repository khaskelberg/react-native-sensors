package com.sensors;

import android.os.Bundle;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.support.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class Accelerometer extends ReactContextBaseJavaModule implements SensorEventListener {

  private final ReactApplicationContext reactContext;
  private final SensorManager sensorManager;
  private final Sensor sensor;
  private long lastReading;
  private long nextGenerationTime;
  private int interval;
  private Arguments arguments;
  private AverageAcceleration avgAcc;

  public Accelerometer(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    this.sensorManager = (SensorManager)reactContext.getSystemService(reactContext.SENSOR_SERVICE);
    this.sensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
  }

  // RN Methods
  @ReactMethod
  public void isAvailable(Promise promise) {
    if (this.sensor == null) {
      // No sensor found, throw error
      promise.reject(new RuntimeException("No Accelerometer found"));
      return;
    }
    promise.resolve(null);
  }

  @ReactMethod
  public void setUpdateInterval(int newInterval) {
    this.interval = newInterval;
  }


  @ReactMethod
  public void startUpdates() {
    // Milisecond to Mikrosecond conversion
    this.lastReading = (System.currentTimeMillis() / this.interval) * this.interval;
    this.nextGenerationTime = this.lastReading + this.interval;
    this.avgAcc = new AverageAcceleration();
    sensorManager.registerListener(this, sensor, sensorManager.SENSOR_DELAY_GAME);
  }

  @ReactMethod
  public void stopUpdates() {
    sensorManager.unregisterListener(this);
  }

  @Override
  public String getName() {
    return "Accelerometer";
  }

  // SensorEventListener Interface
  private void sendEvent(String eventName, @Nullable WritableMap params) {
    try {
      this.reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
    } catch (RuntimeException e) {
      Log.e("ERROR", "java.lang.RuntimeException: Trying to invoke Javascript before CatalystInstance has been set!");
    }
  }

  @Override
  public void onSensorChanged(SensorEvent sensorEvent) {
    Sensor mySensor = sensorEvent.sensor;
    if (mySensor.getType() != Sensor.TYPE_ACCELEROMETER) {
      return;
    }

    long curTime =  System.currentTimeMillis();
    if (curTime > this.nextGenerationTime) {
      this.avgAcc.add(sensorEvent.values[0], sensorEvent.values[1], sensorEvent.values[2], this.nextGenerationTime - this.lastReading);
      sendEvent("Accelerometer", this.avgAcc.calculate(this.nextGenerationTime, interval));    
      this.avgAcc = new AverageAcceleration();
      this.lastReading = this.nextGenerationTime;
      this.nextGenerationTime += this.interval;
    }

    this.avgAcc.add(sensorEvent.values[0], sensorEvent.values[1], sensorEvent.values[2], curTime - this.lastReading);
    this.lastReading = curTime;
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
  }

  public class AverageAcceleration {
    private double x;
    private double y;
    private double z;
    
    public AverageAcceleration() {
      this.x = 0;
      this.y = 0;
      this.z = 0;
    }

    public void add(double x, double y, double z, long weight) {
      this.x += x * weight;
      this.y += y * weight;
      this.z += z * weight;
    }

    public WritableMap calculate(long timestamp, int interval) {
      WritableMap map = arguments.createMap();
      map.putDouble("x", this.x / interval);
      map.putDouble("y", this.y / interval);
      map.putDouble("z", this.z / interval);
      map.putDouble("timestamp", (double)timestamp);
      return map;
    }
  }
}
