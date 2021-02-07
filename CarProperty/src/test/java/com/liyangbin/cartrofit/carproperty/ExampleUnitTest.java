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
        CarPropertyContext.addScopeProvider("test", TestCarContext::new);
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
        }).emptySubscribe();
    }

    @Test
    public void rxFlowConvertTest() {
        Cartrofit.from(TestCarApi.class).trackIntReactive().subscribe(i -> {
            println("each:" + i);
        });
    }

    @Test
    public void registerChangeTest() {
        Cartrofit.from(TestCarApi.class).registerStringChangeListener(new TestCarApi.OnChangeListener() {
            @Override
            public void onChange(String value) {
                println("callback received:" + value);
            }
        });
    }
    @Test
    public void registerChangeAliasTest() {
        Cartrofit.from(TestCarApi.class).registerStringChangeListenerAlias(new TestCarApi.OnChangeListener() {
            @Override
            public void onChange(String value) {
                println("callback alias received:" + value);
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