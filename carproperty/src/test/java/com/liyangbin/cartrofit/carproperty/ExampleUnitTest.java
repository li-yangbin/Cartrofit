package com.liyangbin.cartrofit.carproperty;

import android.car.CarNotConnectedException;

import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.flow.Flow;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {

    private static void println(String msg) {
        System.out.println(msg);
    }

    private static void print(String msg) {
        System.out.print(msg);
    }

    private static void println(int msg) {
        System.out.println(msg);
    }

    private static void println(char msg) {
        System.out.println(msg);
    }

    @BeforeClass
    public static void onStart() {
        Cartrofit.register(new TestCarContext());
    }

    @Test
    public void intGetTest() {
        int value = Cartrofit.from(TestCarApi.class).getIntSignal();
        println("intGetTest:" + value);
    }

    @Test
    public void intSetTest() {
        Cartrofit.from(TestCarApi.class).setIntSignal(10086);
        println("intSetTest:" + Cartrofit.from(TestCarApi.class).getIntSignal());
    }

    @Test
    public void byteGetTest() {
        println("byteGetTest:" + Cartrofit.from(TestCarApi.class).getByteSignal());
    }

    @Test
    public void byteSetTest() {
        Cartrofit.from(TestCarApi.class).setByteSignal(Byte.MAX_VALUE);
        println("byteSetTest:" + Cartrofit.from(TestCarApi.class).getByteSignal());
    }

    @Test
    public void stringGetTest() {
        println("stringGetTest:" + Cartrofit.from(TestCarApi.class).getStringSignal());
    }

    @Test
    public void stringSetTest() {
        Cartrofit.from(TestCarApi.class).setStringSignal("I am a banana");
        println("stringSetTest:" + Cartrofit.from(TestCarApi.class).getStringSignal());
    }

    @Test
    public void stringFlowTrackTest() {
        Cartrofit.from(TestCarApi.class).trackStringSignal()
                .subscribe(ExampleUnitTest::println);
    }

    @Test
    public void stringFlowRestoreTest() {
        Cartrofit.<TestCarContext>defaultContextOf(TestCarApi.class).setTestTrackIntOrString(true);
        Cartrofit.<TestCarContext>defaultContextOf(TestCarApi.class).setTimeOutMillis(1000);
        Cartrofit.from(TestCarApi.class).trackStringSignalRestore()
                .subscribe(ExampleUnitTest::println);
        Flow.delay(2000).doOnEach(i -> {
            println("delay set");
            Cartrofit.from(TestCarApi.class).setStringSignal("index:" + i);
        }).subscribe(t -> {});
    }

    @Test
    public void rxFlowConvertTest() {
        Cartrofit.from(TestCarApi.class).trackIntReactive().subscribe(i -> {
            println("each:" + i);
        });
    }

    @Test
    public void registerIntChangeTest() {
        Cartrofit.from(TestCarApi.class).registerIntChangeListener(new TestCarApi.OnChangeListener() {
            @Override
            public void onChange(int value) {
                println("callback received:" + value);
            }
        });
    }

    @Test
    public void registerChangeAliasTest() {
        Cartrofit.from(TestCarApi.class).registerStringChangeListenerAlias(new TestCarApi.OnChangeListenerAlias() {
            @Override
            public void onChange(String value) {
                println("callback alias received:" + value);
            }

            @Override
            public void onError(Throwable error) {
                println("error:" + error);
                error.printStackTrace();
            }
        });
    }

    @Test
    public void timeoutTest() {
        Cartrofit.<TestCarContext>defaultContextOf(TestCarApi.class).setTimeOutMillis(3000);
        Cartrofit.from(TestCarApi.class).registerIntChangeListener(new TestCarApi.OnChangeListener() {
            @Override
            public void onChange(int value) {
                println("callback received:" + value);
            }
        });
        Cartrofit.from(TestCarApi.class).setIntSignal(10086);
    }

    @Test
    public void debounceTest() {
        Cartrofit.<TestCarContext>defaultContextOf(TestCarApi.class).setDebounceMillis(3000);
        Cartrofit.<TestCarContext>defaultContextOf(TestCarApi.class).setTestTrackIntOrString(true);
        Cartrofit.from(TestCarApi.class).registerIntChangeListener(new TestCarApi.OnChangeListener() {
            @Override
            public void onChange(int value) {
                println("callback received:" + value);
            }
        });
        Cartrofit.from(TestCarApi.class).setIntSignal(10086);
    }

    @Test
    public void exceptionTest() {
        Cartrofit.<TestCarContext>defaultContextOf(TestCarApi.class).setTestException(true);
        try {
            Cartrofit.from(TestCarApi.class).setIntSignalIfThrow(10086);
        } catch (CarNotConnectedException e) {
            println("exception caught " + e);
        }
    }

    @Test
    public void callbackExceptionWithoutCaughtTest() {
        Cartrofit.<TestCarContext>defaultContextOf(TestCarApi.class).setTestException(true);
        Cartrofit.<TestCarContext>defaultContextOf(TestCarApi.class).setTestTrackIntOrString(true);
        Cartrofit.from(TestCarApi.class).registerIntChangeListener(new TestCarApi.OnChangeListener() {
            @Override
            public void onChange(int value) {
                println("callback received:" + value);
            }
        });
    }

    @Test
    public void callbackExceptionTest() {
        Cartrofit.<TestCarContext>defaultContextOf(TestCarApi.class).setTestException(true);
        Cartrofit.<TestCarContext>defaultContextOf(TestCarApi.class).setTestTrackIntOrString(true);
        Cartrofit.from(TestCarApi.class).registerIntErrorChangeListener(new TestCarApi.OnErrorChangeListener() {
            @Override
            public void onChange(int value) {
                println("callback received:" + value);
            }

            @Override
            public void onError(CarPropertyException caught) {
                println("callback error " + caught);
            }
        });
    }

    @Test
    public void unregisterTest() {
        Cartrofit.<TestCarContext>defaultContextOf(TestCarApi.class).setTestTrackIntOrString(true);
        TestCarApi.OnChangeListener listener;
        Cartrofit.from(TestCarApi.class).registerIntChangeListener(listener = new TestCarApi.OnChangeListener() {
            @Override
            public void onChange(int value) {
                println("callback received:" + value);
            }
        });
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Cartrofit.from(TestCarApi.class).unregisterIntChangeListener(listener);
    }

    @Test
    public void unregisterWithAliasTest() {
        TestCarApi.OnChangeListenerAlias listener;
        Cartrofit.from(TestCarApi.class).registerStringChangeListenerAlias(listener = new TestCarApi.OnChangeListenerAlias() {
            @Override
            public void onChange(String value) {
                println("callback received:" + value);
            }
        });
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Cartrofit.from(TestCarApi.class).unregisterStringChangeListenerAlias(listener);
    }

    @Test
    public void delegateIfTest() {
        int getFromDummy = Cartrofit.from(DummyApi.class).getDummyIntSignal();
        println("getFromDummy:" + getFromDummy);
        Cartrofit.from(DummyApi.class).setDummyIntSignal(20086);
        getFromDummy = Cartrofit.from(DummyApi.class).getDummyIntSignal();
        println("getFromDummy after set:" + getFromDummy);
        Cartrofit.from(DummyApi.class).registerDummyStringChangeListenerAlias(new DummyApi.DummyListener() {
            @Override
            public void onChange(String value) {
                println("dummy api onChange:" + value);
            }
        });
    }

    @Test
    public void mixedIfTest() {
        int getFromMix = Cartrofit.from(MixedApi.class).getIntSignal();
        println("getFromMix:" + getFromMix);
        Cartrofit.from(MixedApi.class).setDummyIntSignal(20086);
        getFromMix = Cartrofit.from(MixedApi.class).getIntSignal();
        println("getFromMix after dummySet:" + getFromMix);
        Cartrofit.from(MixedApi.class).registerDummyStringChangeListenerAlias(new DummyApi.DummyListener() {
            @Override
            public void onChange(String value) {
                println("dummy api onChange:" + value);
            }
        });
    }

    @Test
    public void userContextTest() {
        TestCarContext tempContext = new TestCarContext() {
            @Override
            public String toString() {
                return "created by user";
            }
        };
        int getFromMix = tempContext.from(MixedApi.class).getIntSignal();
        println("getFromMix:" + getFromMix);
        tempContext.from(MixedApi.class).setDummyIntSignal(20086);
        getFromMix = tempContext.from(MixedApi.class).getIntSignal();
        println("getFromMix after dummySet:" + getFromMix);
        tempContext.from(MixedApi.class).registerDummyStringChangeListenerAlias(new DummyApi.DummyListener() {
            @Override
            public void onChange(String value) {
                println("dummy api onChange:" + value);
            }
        });
    }

    @AfterClass
    public static void onFinish() {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}