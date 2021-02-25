package com.liyangbin.cartrofit.carproperty;

import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class TestCarContext extends DefaultCarContext implements CarPropertyAccess {

    public static final HashMap<Integer, Combo> typeMockMap = new HashMap<>();
    private boolean testTrackIntOrString;

    public void setTestTrackIntOrString(boolean testInt) {
        testTrackIntOrString = testInt;
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
    public void onRegister() throws CarNotConnectedException {
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
        Thread tester = new Thread(new Runnable() {
            @Override
            public void run() {
                Random random = new Random();
                boolean token = false;
                while (true) {
                    long sleep = (long) (random.nextFloat() * 1000);
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    int key = testTrackIntOrString ? 0 : 2;
                    synchronized (typeMockMap) {
                        Object obj = generateRandomValue(random, typeMockMap.get(key).clazz);
//                        if (obj.getClass().isArray()) {
//                            System.out.println("value change after:" + sleep + "ms key:" + key + " value:" + Arrays.toString((Object[])obj));
//                        } else {
//                            System.out.println("value change after:" + sleep + "ms key:" + key + " value:" + obj + "============================");
//                        }
                        send(new CarPropertyValue<>(key, 0, obj));
                    }
                    token = !token;
                }
            }
        }, "test_thread");
        tester.start();
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
    public TypedCarPropertyAccess<Integer> getIntCarPropertyAccess() {
        return new TypedCarPropertyAccess<Integer>() {
            @Override
            public Integer get(int propertyId, int area) throws CarNotConnectedException {
                return (Integer) typeMockMap.get(propertyId).value;
            }

            @Override
            public void set(int propertyId, int area, Integer value) throws CarNotConnectedException {
                typeMockMap.get(propertyId).value = value;
            }
        };
    }

    @Override
    public TypedCarPropertyAccess<String> getStringCarPropertyAccess() {
        return new TypedCarPropertyAccess<String>() {
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
    public TypedCarPropertyAccess<?> getTypedCarPropertyAccess(Class<?> type) {
        if (type == int[].class) {
            return new TypedCarPropertyAccess<int[]>() {
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
        return super.getTypedCarPropertyAccess(type);
    }
}
