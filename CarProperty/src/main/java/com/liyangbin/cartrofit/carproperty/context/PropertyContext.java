package com.liyangbin.cartrofit.carproperty.context;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;

import com.liyangbin.cartrofit.carproperty.CarPropertyAccess;
import com.liyangbin.cartrofit.carproperty.CarPropertyContext;
import com.liyangbin.cartrofit.carproperty.CarPropertyException;
import com.liyangbin.cartrofit.carproperty.DefaultCarServiceAccess;
import com.liyangbin.cartrofit.flow.Flow;

import java.util.List;

public class PropertyContext extends CarPropertyContext<CarPropertyManager> {

    public PropertyContext(Context context) {
        super(new DefaultCarServiceAccess<>(context, Car.PROPERTY_SERVICE));
        registerOnceFlowSource = false;
    }

    class PropRegisteredSource extends PropertyFlowSource implements CarPropertyManager.CarPropertyEventListener {

        public PropRegisteredSource(int propertyId, int area) {
            super(propertyId, area);
        }

        @Override
        public void finishWithInjector(Flow.Injector<CarPropertyValue<?>> injector) {
            super.finishWithInjector(injector);

            if (getSubscriberCount() == 0 && registered) {
                synchronized (PropertyContext.this) {
                    if (registered) {
                        try {
                            getManagerLazily().unregisterListener(this);
                        } catch (CarNotConnectedException ignore) {
                        }
                        flowSourceList.remove(this);
                        registered = false;
                    }
                }
            }
        }

        @Override
        public void onChangeEvent(CarPropertyValue carPropertyValue) {
            sendPropertyChange(carPropertyValue);
        }

        @Override
        public void onErrorEvent(int propertyId, int area) {
            sendPropertyError(new CarPropertyException(propertyId, area));
        }
    }

    @Override
    public List<CarPropertyConfig> onLoadConfig() throws CarNotConnectedException {
        return getManagerLazily().getPropertyList();
    }

    @Override
    public PropertyFlowSource onCreatePropertyFlowSource(int propertyId, int area) {
        return new PropRegisteredSource(propertyId, area);
    }

    @Override
    public void onRegister(PropertyFlowSource source) throws CarNotConnectedException {
        PropRegisteredSource registeredSource = (PropRegisteredSource) source;
        getManagerLazily().registerListener(registeredSource, registeredSource.propertyId, 0f);
    }

    @Override
    public boolean isPropertyAvailable(int propertyId, int area) throws CarNotConnectedException {
        return getManagerLazily().isPropertyAvailable(propertyId, area);
    }

    @Override
    public CarPropertyAccess<Integer> getIntCarPropertyAccess() {
        return new CarPropertyAccess<Integer>() {
            @Override
            public Integer get(int propertyId, int area) throws CarNotConnectedException {
                return getManagerLazily().getIntProperty(propertyId, area);
            }

            @Override
            public void set(int propertyId, int area, Integer value) throws CarNotConnectedException {
                getManagerLazily().setIntProperty(propertyId, area, value);
            }
        };
    }

    @Override
    public CarPropertyAccess<Boolean> getBooleanCarPropertyAccess() {
        return new CarPropertyAccess<Boolean>() {
            @Override
            public Boolean get(int propertyId, int area) throws CarNotConnectedException {
                return getManagerLazily().getBooleanProperty(propertyId, area);
            }

            @Override
            public void set(int propertyId, int area, Boolean value) throws CarNotConnectedException {
                getManagerLazily().setBooleanProperty(propertyId, area, value);
            }
        };
    }

    @Override
    public CarPropertyAccess<Float> getFloatCarPropertyAccess() {
        return new CarPropertyAccess<Float>() {
            @Override
            public Float get(int propertyId, int area) throws CarNotConnectedException {
                return getManagerLazily().getFloatProperty(propertyId, area);
            }

            @Override
            public void set(int propertyId, int area, Float value) throws CarNotConnectedException {
                getManagerLazily().setFloatProperty(propertyId, area, value);
            }
        };
    }
}
