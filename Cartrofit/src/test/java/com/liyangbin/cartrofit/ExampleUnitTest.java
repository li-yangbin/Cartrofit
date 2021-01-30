package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.FlowPublisher;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

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

    private static Executor defaultBackground
            = Executors.newSingleThreadExecutor(r -> new Thread(r, "background"));
    private static Executor defaultForeground
            = Executors.newSingleThreadExecutor(r -> new Thread(r, "foreground"));

    @BeforeClass
    public static void onStart() {
        println("Flow test starts!!");
    }

    @Test
    public void flowMapTest() {
        generateFlow("mapTest").map(s -> s + " mapped").subscribe(ExampleUnitTest::println);
    }

    @Test
    public void flowFlatMapTest() {
        Flow.interval(1000)
                .takeCount(10)
                .flatMap(i -> generateFlow("index:" + i + " ")
                        .doOnComplete(() -> println("")))
                .subscribe(ExampleUnitTest::print);
    }

    @Test
    public void flowSwitchMapTest() {
        Flow.interval(1000)
                .takeCount(10)
//                .doOnEach(ii -> println("interval on each:" + ii))
                .switchMap(i -> generateFlow("index:" + i)
                        .doOnComplete(() -> println(""))
                        .subscribeOn(defaultBackground))
                .subscribe(ExampleUnitTest::print);
    }

    @Test
    public void flowTimeoutTest() {
        Flow.interval(499)
                .timeout(500)
                .catchException(TimeoutException.class, cc -> cc.printStackTrace((System.out)))
                .subscribe(ExampleUnitTest::println);
    }

    @Test
    public void flowDistinctTest() {
        Flow.just("as", "as", "sd", "sd", "fg", "fg").distinct().subscribe(ExampleUnitTest::println);
    }

    @Test
    public void flowScheduleTest() {
        Flow.just("as", "as", "sd", "sd", "fg", "fg").doOnEach(ss -> {
            try {
                Thread.sleep(500);
                println("emit " + ss + " on:" + Thread.currentThread());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).subscribeOn(defaultBackground).consumeOn(defaultForeground)
                .doOnEach(ss -> {
                    println("consume " + ss + " on:" + Thread.currentThread());
                }).subscribe(ExampleUnitTest::println);
    }

    @Test
    public void flowPublishTest() {
        FlowPublisher<String> publisher = Flow.just("as", "as", "sd", "sd", "fg", "fg").doOnEach(ss -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).publish();
        publisher.share().subscribeOn(defaultForeground).subscribe(ExampleUnitTest::println);
        publisher.share().consumeOn(defaultBackground).distinct().subscribe(ExampleUnitTest::print);
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
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (!cancel) {
                    injector.done();
                }
            }
        });
    }

    @AfterClass
    public static void onStop() {
        try {
            Thread.sleep(10000);
            println("Flow test ends!!");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}