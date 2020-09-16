package com.liyangbin.carretrofit;

import android.car.hardware.CarPropertyValue;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Random;

public class TestCarManager extends CarManager2 {

    public static final HashMap<Integer, Combo<?>> typeMockMap = new HashMap<>();

    public static class Combo<T> {
        public Class<T> clazz;
        public T value;

        public Combo(Class<T> clazz, T value) {
            this.clazz = clazz;
            this.value = value;
        }
    }

    static {
        typeMockMap.put(0, new Combo<>(int.class, 10));
        typeMockMap.put(1, new Combo<>(int[].class, new int[]{10}));

        typeMockMap.put(2, new Combo<>(String.class, "hello"));
        typeMockMap.put(3, new Combo<>(String[].class, new String[]{"hello"}));

        typeMockMap.put(4, new Combo<>(byte.class, (byte) 12));
        typeMockMap.put(5, new Combo<>(byte[].class, new byte[]{12, 100, 34, 12, 43, 1, 87, 59}));

        typeMockMap.put(6, new Combo<>(int.class, 20));
        typeMockMap.put(7, new Combo<>(int[].class, new int[]{20}));

        typeMockMap.put(8, new Combo<>(String.class, "world"));
        typeMockMap.put(9, new Combo<>(String[].class, new String[]{"world"}));
    }

    {
        Thread tester = new Thread(new Runnable() {
            @Override
            public void run() {
                Random random = new Random();
                boolean token = false;
                while (true) {
                    long sleep = (long) (random.nextFloat() * 1000 + 1000);
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    int key = token ? 2 : 0/*random.nextInt(10)*/;
                    synchronized (typeMockMap) {
                        Object obj = generateRandomValue(random, typeMockMap.get(key).clazz);
//                        if (obj.getClass().isArray()) {
//                            System.out.println("value change after:" + sleep + "ms key:" + key + " value:" + Arrays.toString((Object[])obj));
//                        } else {
                            System.out.println("value change after:" + sleep + "ms key:" + key + " value:" + obj);
//                        }
                        notifyChange(new CarPropertyValue<>(key, 0, obj));
                    }
                    token = !token;
                }
            }
        }, "test_thread");
        tester.start();
    }

    @Override
    public void notifyChange(CarPropertyValue<?> value) {
        super.notifyChange(value);
        Combo<Object> combo = (Combo<Object>) typeMockMap.get(value.getPropertyId());
        combo.value = value;
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
    public void trackRootIfNeeded() {
    }

    @Override
    public synchronized  <Value> Value get(int key, int area, CarType type) {
        return (Value) typeMockMap.get(key).value;
    }

    @Override
    public synchronized <Value> void set(int key, int area, Value value) {
        Combo<Value> combo = (Combo<Value>) typeMockMap.get(key);
        combo.value = value;
//        notifyChange(key, value);
    }

    @Nullable
    @Override
    public synchronized <Value> Class<Value> extractValueType(int key) {
        return (Class<Value>) typeMockMap.get(key).clazz;
    }
}
