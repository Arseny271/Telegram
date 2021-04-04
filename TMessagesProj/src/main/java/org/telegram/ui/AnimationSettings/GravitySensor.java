package org.telegram.ui.AnimationSettings;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import static android.content.Context.SENSOR_SERVICE;

public class GravitySensor {
    private SensorManager sensorManager;
    private Sensor gravitySensor;
    private SensorEventListener listener;

    private float interpolatedX = 0f;
    private float interpolatedY = 0f;
    private float interpolatedZ = 0f;
    private final int interpolatedSize = 24;
    private boolean firstValue = true;

    private GravitySensorDelegate delegate;
    public interface GravitySensorDelegate {
        void onGravityUpdate(float x, float y, float z);
    }

    public GravitySensor(Context context) {
        try {
            sensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
            gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            listener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent sensorEvent) {
                    if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                        float axisX = sensorEvent.values[0];
                        float axisY = sensorEvent.values[1];
                        float axisZ = sensorEvent.values[2];
                        float total = Math.abs(axisX) + Math.abs(axisY) + Math.abs(axisZ);
                        axisX /= total;
                        axisY /= total;
                        axisZ /= total;

                        if (firstValue) {
                            interpolatedX = axisX * interpolatedSize;
                            interpolatedY = axisY * interpolatedSize;
                            interpolatedZ = axisZ * interpolatedSize;
                            firstValue = false;
                        }

                        interpolatedX += axisX - (interpolatedX / interpolatedSize);
                        interpolatedY += axisY - (interpolatedY / interpolatedSize);
                        interpolatedZ += axisZ - (interpolatedZ / interpolatedSize);

                        if (delegate != null) {
                            delegate.onGravityUpdate(interpolatedX, interpolatedY, interpolatedZ);
                        }
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int i) {

                }
            };
        } catch (Exception e) {

        }
    }

    public void setDelegate(GravitySensorDelegate delegate) {
        this.delegate = delegate;
    }

    public void listenStart() {
        if (gravitySensor == null || sensorManager == null || listener == null) return;
        sensorManager.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_UI);
    }

    public void listenStop() {
        if (gravitySensor == null || sensorManager == null || listener == null) return;
        sensorManager.unregisterListener(listener, gravitySensor);
    }
}
