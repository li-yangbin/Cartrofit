package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;

import androidx.annotation.Nullable;

import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.funtion.Function2;

import java.util.HashMap;
import java.util.Random;

@Scope("test")
public class TestCarManager extends CarManager2 {

    public static final HashMap<Integer, Combo> typeMockMap = new HashMap<>();

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
                            System.out.println("value change after:" + sleep + "ms key:" + key + " value:" + obj + "============================");
//                        }
                        notifyChange(new CarPropertyValue<>(key, 0, obj));
                    }
                    token = !token;
                }
            }
        }, "test_thread");
        tester.start();
    }

//    @Override
//    public void onCommandCreate(Command command) {
//        switch (command.getId()) {
//            case trackIntReactive:
//                System.out.println("onCommandCreate trackIntReactive " + command);
//                command.addInterceptorToTop(new Interceptor() {
//                    @Override
//                    public Object process(Command command, Object parameter) throws Throwable {
//                        System.out.println("interceptor trackIntReactive " + command);
//                        return command.invoke(parameter);
//                    }
//                });
//                break;
//            case trackIntReactiveAlias:
//                System.out.println("onCommandCreate trackIntReactiveAlias " + command);
//                command.addInterceptorToTop(new Interceptor() {
//                    @Override
//                    public Object process(Command command, Object parameter) throws Throwable {
//                        System.out.println("interceptor11 trackIntReactiveAlias " + command);
//                        return command.invoke(parameter);
//                    }
//                });
//                break;
//            case trackIntAndBoolean:
//                System.out.println("onCommandCreate trackIntAndBoolean " + command);
//                command.addInterceptorToTop(new Interceptor() {
//                    @Override
//                    public Object process(Command command, Object parameter) throws Throwable {
//                        System.out.println("interceptor22 trackIntAndBoolean " + command);
//                        return command.invoke(parameter);
//                    }
//                });
//                break;
//            case trackStringSignal:
//                System.out.println("onCommandCreate trackStringSignal " + command);
//                command.addInterceptorToTop(new Interceptor() {
//                    @Override
//                    public Object process(Command command, Object parameter) throws Throwable {
//                        System.out.println("interceptor33 trackStringSignal " + command);
//                        return command.invoke(parameter);
//                    }
//                });
//                break;
//        }
//    }

    Function2<String, Boolean, String> combinator_aa = new Function2<String, Boolean, String>() {

        @Override
        public String apply(String value1, Boolean value2) {
            return value1 + " assemble " + value2;
        }
    };

    Function2<String, String, String> combinator_bb = new Function2<String, String, String>() {

        @Override
        public String apply(String value1, String value2) {
            return value1 + " assemble 22 " + value2;
        }
    };

    @Override
    public void notifyChange(CarPropertyValue<?> value) {
        super.notifyChange(value);
        Combo combo = typeMockMap.get(value.getPropertyId());
        combo.value = value.getValue();
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
    public synchronized Object get(int key, int area, CarType type) {
        return typeMockMap.get(key).value;
    }

    @Override
    public synchronized void set(int key, int area, Object value) {
        Combo combo = typeMockMap.get(key);
        combo.value = value;
        System.out.println("set key:" + key + " value:" + value);
        notifyChange(new CarPropertyValue<>(key, area, value));
    }

    @Nullable
    @Override
    public synchronized Class<?> extractValueType(int key) {
        return typeMockMap.get(key).clazz;
    }
}
