package com.liyangbin.cartrofit;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.function.Consumer;

public final class ConnectHelper<T> {
    private static boolean sConnected;
    private static boolean sConnecting;
    private static Car sCar;

    private static final ArrayList<Runnable> sConnectAction = new ArrayList<>();

    private final Class<T> apiClass;

    private ConnectHelper(Class<T> apiClass) {
        this.apiClass = apiClass;
    }

    public static <T> ConnectHelper<T> from(Class<T> apiClass) {
        return new ConnectHelper<>(apiClass);
    }

    public void onConnect(Consumer<T> consumer) {
        if (isConnected()) {
            consumer.accept(Cartrofit.from(apiClass));
        } else {
            addOnConnectAction(() -> consumer.accept(Cartrofit.from(apiClass)));
        }
    }

    public static void ensureConnect(Context context) {
        ensureConnect(context, null);
    }

    public static void ensureConnect(Context context, Runnable onConnect) {
        if (onConnect != null) {
            if (sConnected) {
                onConnect.run();
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
                        sConnectAction.get(i).run();
                    }
                    sConnectAction.clear();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    sConnected = sConnecting = false;
                    sConnectAction.clear();
                }
            });
        }
    }

    public static void addOnConnectAction(Runnable onConnected) {
        if (sConnected) {
            onConnected.run();
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

    public static <T> T get(String key) throws CarNotConnectedException {
        return (T) sCar.getCarManager(key);
    }
}
