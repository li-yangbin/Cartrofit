package com.liyangbin.cartrofit;

import android.car.Car;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.function.Consumer;

class ConnectHelper<T> {
    private static boolean sConnected;
    private static boolean sConnecting;
    private static Car sCar;

    private static final ArrayList<Consumer<Car>> sConnectAction = new ArrayList<>();

    static void ensureConnect(Context context) {
        ensureConnect(context);
    }

    static void ensureConnect(Context context, Consumer<Car> onConnect) {
        if (onConnect != null) {
            if (sConnected) {
                onConnect.accept(sCar);
                return;
            }
            sConnectAction.add(onConnect);
        }
        if (!sConnected && !sConnecting) {
            sConnecting = true;
            sCar = Car.createCar(context.getApplicationContext(), new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    sConnected = true;
                    sConnecting = false;
                    for (int i = 0; i < sConnectAction.size(); i++) {
                        sConnectAction.get(i).accept(sCar);
                    }
                    sConnectAction.clear();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    sConnected = sConnecting = false;
                    sConnectAction.clear();
                }
            });
            sCar.connect();
        }
    }

    public static void addOnConnectAction(Consumer<Car> onConnected) {
        if (sConnected) {
            onConnected.accept(sCar);
            return;
        }
        sConnectAction.add(onConnected);
    }

    public static void removeOnConnectAction(Runnable action) {
        sConnectAction.remove(action);
    }

    public static boolean isConnected() {
        return sConnected;
    }

//    public static <T> T get(String key) throws CarNotConnectedException {
//        return (T) sCar.getCarManager(key);
//    }

    public static Car get() {
        return sCar;
    }
}
