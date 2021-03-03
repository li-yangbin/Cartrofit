package com.liyangbin.cartrofit.carproperty;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.Objects;

public class DefaultCarServiceAccess<CAR> implements CarServiceAccess<CAR> {
    private static boolean sConnected;
    private static boolean sConnecting;
    private static Car sCar;
    private static final ArrayList<CarAvailabilityListener> sAvailabilityListener = new ArrayList<>();

    private final String managerKey;
    private final Context context;

    public DefaultCarServiceAccess(Context context, String managerKey) {
        this.context = context;
        this.managerKey = managerKey;
    }

    @Override
    public void tryConnect() {
        ensureConnect(context);
    }

    @Override
    @SuppressWarnings("unchecked")
    public CAR get() throws CarNotConnectedException {
        if (sCar == null || !sConnected) {
            throw new CarNotConnectedException("CarService has not connected yet");
        }
        try {
            return (CAR) sCar.getCarManager(managerKey);
        } catch (Throwable throwable) {
            throw new CarNotConnectedException("getCarManager failed", throwable);
        }
    }

    @Override
    public boolean isAvailable() {
        return sConnected;
    }

    @Override
    public void addOnCarAvailabilityListener(CarAvailabilityListener listener) {
        Objects.requireNonNull(listener);
        sAvailabilityListener.add(listener);
        if (sConnected) {
            listener.onCarAvailable();
        }
    }

    public static void ensureConnect(Context context) {
        if (!sConnected && !sConnecting) {
            sConnecting = true;
            sCar = Car.createCar(context, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    sConnected = true;
                    sConnecting = false;
                    for (int i = 0; i < sAvailabilityListener.size(); i++) {
                        sAvailabilityListener.get(i).onCarAvailable();
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    sConnected = sConnecting = false;
                    sCar = null;
                    for (int i = 0; i < sAvailabilityListener.size(); i++) {
                        sAvailabilityListener.get(i).onCarUnavailable();
                    }
                }
            });
            sCar.connect();
        }
    }
}
