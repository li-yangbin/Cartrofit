package com.liyangbin.cartrofit.carproperty;

import android.car.CarNotConnectedException;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CartrofitContext;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.LifeAwareHotFlowSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;

public abstract class CarAbstractContext<CAR, SOURCE_KEY, SOURCE_DATA_TYPE> extends CartrofitContext<CarPropertyScope> {

    private final HashMap<SOURCE_KEY, CarFlowSource> cachedFlowSource = new HashMap<>();
    private boolean cacheDirty = true;
    private ArrayList<CarFlowSource> copyAfterDirtyFlowSourceList;
    private final CarServiceAccess<CAR> serviceAccess;
    private CAR manager;
    private boolean globalAvailabilityRegistered;
    private boolean globalRegistered;
    private boolean stickySupport;
    private final CarAvailabilityListener globalAvailabilityListener = new CarAvailabilityListener() {
        @Override
        public void onCarAvailable() {
            synchronized (CarAbstractContext.this) {
                globalAvailabilityRegistered = false;
                serviceAccess.removeOnCarAvailabilityListener(this);
                try {
                    onGlobalRegister(true);
                    globalRegistered = true;
                    for (CarFlowSource flowSource : cachedFlowSource.values()) {
                        if (flowSource.isActive()) {
                            onRegister(true, flowSource);
                            if (stickySupport) {
                                flowSource.invalidateStickyDataForced();
                            }
                        }
                    }
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

    public CarServiceAccess<CAR> getCarAccess() {
        return this.serviceAccess;
    }

    @Override
    public boolean onApiCreate(CarPropertyScope annotation, Class<?> apiType) {
        return annotation.value().equals(serviceAccess.getKey());
    }

    public boolean isStickySupport() {
        return stickySupport;
    }

    public void setStickySupport(boolean stickySupport) {
        this.stickySupport = stickySupport;
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

    public synchronized CarFlowSource getOrCreateFlowSource(SOURCE_KEY sourceKey) {
        CarFlowSource source = cachedFlowSource.get(sourceKey);
        if (source == null) {
            cachedFlowSource.put(sourceKey, source = onCreateFlowSource(sourceKey));
            cacheDirty = true;
        }
        return source;
    }

    public synchronized final <T extends CarFlowSource> Collection<T> getAllFlowSourceInQuick() {
        if (cacheDirty) {
            copyAfterDirtyFlowSourceList = new ArrayList<>(cachedFlowSource.values());
            cacheDirty = false;
        }
        return (Collection<T>) copyAfterDirtyFlowSourceList;
    }

    public abstract CarFlowSource onCreateFlowSource(SOURCE_KEY sourceKey);

    public void onRegister(boolean register, CarFlowSource flowSource) throws CarNotConnectedException {
    }

    public abstract void onGlobalRegister(boolean register) throws CarNotConnectedException;

    public abstract class CarFlowSource extends LifeAwareHotFlowSource<SOURCE_DATA_TYPE> {

        public SOURCE_KEY sourceKey;
        private SOURCE_DATA_TYPE stickyData;

        public CarFlowSource(SOURCE_KEY sourceKey) {
            this.sourceKey = sourceKey;
        }

        @Override
        public void startWithInjector(Flow.Injector<SOURCE_DATA_TYPE> injector) {
            super.startWithInjector(injector);
            if (stickySupport) {
                SOURCE_DATA_TYPE stickyData = getStickyData(true);
                if (stickyData != null) {
                    injector.send(stickyData);
                }
            }
        }

        void invalidateStickyDataForced() {
            SOURCE_DATA_TYPE stickyData = getStickyData(false);
            if (stickyData != null) {
                publish(stickyData);
            }
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
                    globalAvailabilityRegistered = true;
                    serviceAccess.addOnCarAvailabilityListener(globalAvailabilityListener);
                    serviceAccess.tryConnect();
                }
            }
        }

        @Override
        public void onInactive() {
            synchronized (CarAbstractContext.this) {
                cachedFlowSource.remove(sourceKey);
                cacheDirty = true;
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

        @Override
        public void publish(SOURCE_DATA_TYPE value) {
            if (stickySupport) {
                synchronized (this) {
                    stickyData = value;
                }
            }
            super.publish(value);
        }

        public SOURCE_DATA_TYPE getStickyData(boolean useCache) {
            synchronized (this) {
                if (useCache && stickyData != null) {
                    return stickyData;
                }
                if (!serviceAccess.isAvailable()) {
                    return null;
                }
                try {
                    return stickyData = loadInitData();
                } catch (CarNotConnectedException issue) {
                    throw new RuntimeException("impossible", issue);
                }
            }
        }

        public SOURCE_DATA_TYPE loadInitData() throws CarNotConnectedException {
            throw new IllegalStateException("Sub-class should implement this method in sticky context");
        }
    }
}
