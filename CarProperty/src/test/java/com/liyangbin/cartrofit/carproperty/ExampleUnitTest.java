package com.liyangbin.cartrofit.carproperty;

import com.liyangbin.cartrofit.flow.Flow;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.Executors;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {

    private static void println(String msg) {
        System.out.println(msg);
    }

    private static void println(int msg) {
        System.out.println(msg);
    }

    private static void println(char msg) {
        System.out.println(msg);
    }

    @BeforeClass
    public static void onStart() {
        CarPropertyContext.addCarPropertyHandler("test", TestCarContext::new);
    }

    @Test
    public void intGetTest() {
        int value = CarPropertyContext.fromScope(TestCarApi.class).getIntSignal();
        println(value);
    }

    @Test
    public void intSetTest() {
        CarPropertyContext.fromScope(TestCarApi.class).setIntSignal(10086);
        println(CarPropertyContext.fromScope(TestCarApi.class).getIntSignal());
    }

    @Test
    public void stringFlowTrack() {
        CarPropertyContext.fromScope(TestCarApi.class).trackStringSignal()
                .switchMap(s -> Flow.fromSource(new Flow.ColdFlowSource<Character>() {
                    String sss = s;
                    boolean cancel;
            @Override
            public void onStop() {
                cancel = true;
                println("===============onStop!!!!");
            }

            @Override
            public void startWithInjector(Flow.Injector<Character> injector) {
                println("===============start " + sss);
                for (int i = 0; i < sss.length() && !cancel; i++) {
                    injector.send(sss.charAt(i));
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (cancel) {
                    println("=================Cancel!!!! for:" + sss);
                } else {
                    println("===============Done!!!! for:" + sss);
                    injector.done();
                }
            }
        }).subscribeOn(Executors.newSingleThreadExecutor())).subscribe(ExampleUnitTest::println);
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