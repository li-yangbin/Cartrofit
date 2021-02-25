package com.liyangbin.cartrofit.carproperty;

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
        CarPropertyContext.registerScopeProvider("test", TestCarContext::new);
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
        Cartrofit.<TestCarContext>contextOf(TestCarApi.class).setTimeOutMillis(3000);
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
        Cartrofit.<TestCarContext>contextOf(TestCarApi.class).setDebounceMillis(7000);
        Cartrofit.<TestCarContext>contextOf(TestCarApi.class).setTestTrackIntOrString(true);
        Cartrofit.from(TestCarApi.class).registerIntChangeListener(new TestCarApi.OnChangeListener() {
            @Override
            public void onChange(int value) {
                println("callback received:" + value);
            }
        });
        Cartrofit.from(TestCarApi.class).setIntSignal(10086);
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