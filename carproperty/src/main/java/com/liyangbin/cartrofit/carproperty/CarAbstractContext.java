package com.liyangbin.cartrofit.carproperty;

import android.car.CarNotConnectedException;

import com.liyangbin.cartrofit.CartrofitContext;
import com.liyangbin.cartrofit.flow.LifeAwareHotFlowSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;

public abstract class CarAbstractContext<CAR, SOURCE_KEY, SOURCE_DATA_TYPE> extends CartrofitContext {

    private HashMap<SOURCE_KEY, CarFlowSource> cachedFlowSource = new HashMap<>();
    private boolean cachedDirty = true;
    private ArrayList<CarFlowSource> copyAfterDirtyFlowSourceList;
    private CarServiceAccess<CAR> serviceAccess;
    private CAR manager;
    private boolean globalAvailabilityRegistered;
    private boolean globalRegistered;
    private final CarAvailabilityListener globalAvailabilityListener = new CarAvailabilityListener() {
        @Override
        public void onCarAvailable() {
            serviceAccess.removeOnCarAvailabilityListener(this);
            synchronized (CarAbstractContext.this) {
                try {
                    onRegister(true, null);
                } catch (CarNotConnectedException connectedException) {
                    throw new RuntimeException("impossible", connectedException);
                }
            }
        }

        @Override
        public void onCarUnavailable() {
            synchronized (CarAbstractContext.this) {
                manager = null;
            }
        }
    };

    public CarAbstractContext(CarServiceAccess<CAR> serviceAccess) {
        this.serviceAccess = Objects.requireNonNull(serviceAccess);
    }

    public CAR getManagerLazily() throws CarNotConnectedException {
        if (manager != null) {
            return manager;
        }
        synchronized (this) {
            if (manager == null) {
                manager = serviceAccess.get();
            }
            return manager;
        }
    }

    public synchronized final CarFlowSource getOrCreateFlowSource(SOURCE_KEY sourceKey) {
        CarFlowSource source = cachedFlowSource.get(sourceKey);
        if (source == null) {
            source = onCreateFlowSource(sourceKey);
            cachedFlowSource.put(sourceKey, source);
            cachedDirty = true;
        }
        return source;
    }

    public synchronized final <T extends CarFlowSource> Collection<T> getAllFlowSourceInQuick() {
        if (cachedDirty) {
            copyAfterDirtyFlowSourceList = new ArrayList<>(cachedFlowSource.values());
            cachedDirty = false;
        }
        return (Collection<T>) copyAfterDirtyFlowSourceList;
    }

    public abstract CarFlowSource onCreateFlowSource(SOURCE_KEY sourceKey);

    public void onRegister(boolean register, CarFlowSource flowSource) throws CarNotConnectedException {
    }

    public abstract void onGlobalRegister(boolean register) throws CarNotConnectedException;

    public synchronized boolean hasFlowSourceAlive() {
        return cachedFlowSource.size() > 0;
    }

    public abstract class CarFlowSource extends LifeAwareHotFlowSource<SOURCE_DATA_TYPE> {

        public SOURCE_KEY sourceKey;

        public CarFlowSource(SOURCE_KEY sourceKey) {
            this.sourceKey = sourceKey;
        }

        @Override
        public void onActive() {
            synchronized (CarAbstractContext.this) {
                if (serviceAccess.isAvailable()) {
                    try {
                        if (!globalRegistered) {
                            onGlobalRegister(true);
                            globalRegistered = true;
                        }
                        onRegister(true, this);
                    } catch (CarNotConnectedException error) {
                        throw new RuntimeException("impossible situation", error);
                    }
                } else if (!globalAvailabilityRegistered) {
                    serviceAccess.addOnCarAvailabilityListener(globalAvailabilityListener);
                    serviceAccess.tryConnect();
                    globalAvailabilityRegistered = true;
                }
            }
        }

        @Override
        public void onInactive() {
            synchronized (CarAbstractContext.this) {
                cachedFlowSource.remove(sourceKey);
                cachedDirty = true;
                boolean doGlobalUnregister = cachedFlowSource.size() == 0;
                if (globalAvailabilityRegistered && doGlobalUnregister) {
                    serviceAccess.removeOnCarAvailabilityListener(globalAvailabilityListener);
                    globalAvailabilityRegistered = false;
                }
                if (serviceAccess.isAvailable()) {
                    try {
                        onRegister(false, this);
                        if (globalRegistered && doGlobalUnregister) {
                            onGlobalRegister(false);
                            globalRegistered = false;
                        }
                    } catch (CarNotConnectedException error) {
                        throw new RuntimeException("impossible situation", error);
                    }
                }
            }
        }
    }
}
