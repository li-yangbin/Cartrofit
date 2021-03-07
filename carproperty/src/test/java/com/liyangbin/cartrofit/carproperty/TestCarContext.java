package com.liyangbin.cartrofit.carproperty;

import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class TestCarContext extends CarPropertyContext<Object> {

    public static final HashMap<Integer, Combo> typeMockMap = new HashMap<>();
    private boolean testTrackIntOrString;
    private boolean testException;
    private Thread dispatcher;

    public TestCarContext() {
        super(new DummyAccess());
    }

    @Override
    public void onGlobalRegister(boolean register) throws CarNotConnectedException {
        if (register) {
            dispatcher.start();
            System.out.println("test dispatcher start");
        } else {
            dispatcher.interrupt();
            System.out.println("test dispatcher interrupt");
        }
    }

    private static class DummyAccess implements CarServiceAccess<Object> {

        @Override
        public void tryConnect() {
        }

        @Override
        public Object get() throws CarNotConnectedException {
            return null;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void addOnCarAvailabilityListener(CarAvailabilityListener listener) {
        }

        @Override
        public void removeOnCarAvailabilityListener(CarAvailabilityListener listener) {
        }
    }

    public void setTestTrackIntOrString(boolean testInt) {
        testTrackIntOrString = testInt;
    }

    public void setTestException(boolean testException) {
        this.testException = testException;
    }

    @Override
    public CarPropertyConfig<?> getConfig(int propertyId, int area) throws CarNotConnectedException {
        Combo combo = typeMockMap.get(propertyId);
        return CarPropertyConfig.newBuilder(combo.clazz, propertyId, area).build();
    }

    @Override
    public List<CarPropertyConfig> onLoadConfig() throws CarNotConnectedException {
        ArrayList<CarPropertyConfig> result = new ArrayList<>();
        for (Map.Entry<Integer, Combo> entry : typeMockMap.entrySet()) {
            result.add(CarPropertyConfig.newBuilder(entry.getValue().clazz, entry.getKey(), 0).build());
        }
        return result;
    }

    @Override
    public boolean isPropertyAvailable(int propertyId, int area) throws CarNotConnectedException {
        return typeMockMap.containsKey(propertyId);
    }

    public static class Combo {
        public Class<?> clazz;
        public Object value;

        public Combo(Class<?> clazz, Object value) {
            this.clazz = clazz;
            this.value = value;
        }
    }

    static {
        typeMockMap.put(0, new Combo(int.class, 10));
        typeMockMap.put(1, new Combo(int[].class, new int[]{10}));

        typeMockMap.put(2, new Combo(String.class, "hello"));
        typeMockMap.put(3, new Combo(String[].class, new String[]{"hello"}));

        typeMockMap.put(4, new Combo(byte.class, (byte) 12));
        typeMockMap.put(5, new Combo(byte[].class, new byte[]{12, 100, 34, 12, 43, 1, 87, 59}));

        typeMockMap.put(6, new Combo(int.class, 20));
        typeMockMap.put(7, new Combo(int[].class, new int[]{20}));

        typeMockMap.put(8, new Combo(String.class, "world"));
        typeMockMap.put(9, new Combo(String[].class, new String[]{"world"}));
    }

    {
        dispatcher = new Thread(new Runnable() {
            @Override
            public void run() {
                Random random = new Random();
                int counter = 0;
                while (true) {
                    long sleep = (long) (random.nextFloat() * 1000);
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                    int key = testTrackIntOrString ? 0 : 2;
                    synchronized (typeMockMap) {
                        Object obj = generateRandomValue(random, typeMockMap.get(key).clazz);
//                        if (obj.getClass().isArray()) {
//                            System.out.println("value change after:" + sleep + "ms key:" + key + " value:" + Arrays.toString((Object[])obj));
//                        } else {
                            System.out.println("dispatch value change key:" + key + " value:" + obj + "============================");
//                        }
                        if (testException && counter % 3 == 0) {
                            error(key, 0);
                        } else {
                            send(new CarPropertyValue<>(key, 0, obj));
                        }
                    }
                    counter ++;
                }
            }
        }, "test_thread");
    }

    static Object generateRandomValue(Random random, Class<?> clazz) {
        if (String.class == clazz) {
            String[] strRand = {"I", "am", "a", "big", "red", "apple"};
            return strRand[random.nextInt(strRand.length)];
        } else if (String[].class == clazz) {
            return new String[]{"I", "am", "a", "big", "red", "apple"};
        }
        else if (int.class == clazz) {
            return random.nextInt(100) - 50;
        } else if (int[].class == clazz) {
            return new int[] {random.nextInt(100) - 50};
        }
        else if (byte.class == clazz) {
            return (byte) random.nextInt(100) - 50;
        } else if (byte[].class == clazz) {
            byte[] out = new byte[5];
            random.nextBytes(out);
            return out;
        }
        else if (float.class == clazz) {
            return random.nextFloat();
        } else if (float[].class == clazz) {
            return new float[] {random.nextFloat()};
        }
        else {
            throw new IllegalArgumentException("invalid type:" + clazz);
        }
    }

    @Override
    public CarPropertyAccess<Integer> getIntCarPropertyAccess() {
        return new CarPropertyAccess<Integer>() {
            @Override
            public Integer get(int propertyId, int area) throws CarNotConnectedException {
                return (Integer) typeMockMap.get(propertyId).value;
            }

            @Override
            public void set(int propertyId, int area, Integer value) throws CarNotConnectedException {
                if (testException) {
                    throw new CarNotConnectedException("tesst");
                } else {
                    typeMockMap.get(propertyId).value = value;
                }
            }
        };
    }

    @Override
    public CarPropertyAccess<String> getStringCarPropertyAccess() {
        return new CarPropertyAccess<String>() {
            @Override
            public String get(int propertyId, int area) throws CarNotConnectedException {
                return (String) typeMockMap.get(propertyId).value;
            }

            @Override
            public void set(int propertyId, int area, String value) throws CarNotConnectedException {
                typeMockMap.get(propertyId).value = value;
            }
        };
    }

    @Override
    public CarPropertyAccess<Byte> getByteCarPropertyAccess() {
        return new CarPropertyAccess<Byte>() {
            @Override
            public Byte get(int propertyId, int area) throws CarNotConnectedException {
                return (Byte) typeMockMap.get(propertyId).value;
            }

            @Override
            public void set(int propertyId, int area, Byte value) throws CarNotConnectedException {
                typeMockMap.get(propertyId).value = value;
            }
        };
    }

    @Override
    public CarPropertyAccess<?> getCarPropertyAccess(Class<?> type) {
        if (type == int[].class) {
            return new CarPropertyAccess<int[]>() {
                @Override
                public int[] get(int propertyId, int area) throws CarNotConnectedException {
                    return (int[]) typeMockMap.get(propertyId).value;
                }

                @Override
                public void set(int propertyId, int area, int[] value) throws CarNotConnectedException {
                    typeMockMap.get(propertyId).value = value;
                }
            };
        }
        return super.getCarPropertyAccess(type);
    }
}
