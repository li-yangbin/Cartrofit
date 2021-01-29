package com.liyangbin.cartrofit;

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

    private static void println(int msg) {
        System.out.println(msg);
    }

    private static void println(char msg) {
        System.out.println(msg);
    }

    private static void print(String msg) {
        System.out.print(msg);
    }

    private static void print(int msg) {
        System.out.print(msg);
    }

    private static void print(char msg) {
        System.out.print(msg);
    }

    @BeforeClass
    public static void onStart() {
        new Thread(new Runnable() {
            @Override
            public void run() {

            }
        }).start();
    }

    @Test
    public void flowMapTest() {
        generateFlow("mapTest").map(s -> s + " mapped").subscribe(ExampleUnitTest::println);
    }

    @Test
    public void flowFlatMapTest() {
        Flow.interval(1000).takeCount(10).flatMap(i -> generateFlow("index:" + i + " message")).doOnComplete(() -> println("")).subscribe(ExampleUnitTest::print);
    }

    private Flow<Character> generateFlow(String source) {
        return Flow.fromSource(new Flow.ColdFlowSource<Character>() {
            boolean cancel;
            @Override
            public void onStop() {
                cancel = true;
            }

            @Override
            public void startWithInjector(Flow.Injector<Character> injector) {
                for (int i = 0; i < source.length() && !cancel; i++) {
                    injector.send(source.charAt(i));
                }
                injector.done();
            }
        });
    }

    @AfterClass
    public static void onStop() {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}