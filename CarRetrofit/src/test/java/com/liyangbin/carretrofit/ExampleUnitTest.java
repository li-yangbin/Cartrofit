package com.liyangbin.carretrofit;

import com.liyangbin.carretrofit.annotation.Apply;
import com.liyangbin.carretrofit.annotation.CarApi;
import com.liyangbin.carretrofit.annotation.Delegate;
import com.liyangbin.carretrofit.annotation.Get;
import com.liyangbin.carretrofit.annotation.Inject;
import com.liyangbin.carretrofit.annotation.Set;
import com.liyangbin.carretrofit.annotation.Track;

import androidx.databinding.ObservableBoolean;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {

    private static class Sub extends Base {

    }

    private static class Base implements Converter2 {
        @Override
        public Object convert(Object value) {
            return null;
        }
    }

    private interface Converter2 extends Converter<Object, Object> {
    }

//    @Test
//    public void otherJava() {
//        Class<?> clazz = CarRetrofit.lookUp(Sub.class);
//        CarRetrofit.addConverter(new Sub());
//    }

//    @Test
    public void priorityLoad() {
        Observable<String> local = Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                print("local load start:" + Thread.currentThread());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
//                    emitter.onError(e);
//                    return;
                }
                emitter.onNext("cache");
                print("local load end:" + Thread.currentThread() + " is disp:" + emitter.isDisposed());
                emitter.onComplete();
            }
        }).subscribeOn(Schedulers.io());
        Observable<String> online = Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                print("online load start:" + Thread.currentThread());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
//                    emitter.onError(e);
//                    return;
                }
                emitter.onNext("online");
                print("online load end:" + Thread.currentThread() + " is disp:" + emitter.isDisposed());
                emitter.onComplete();
            }
        }).subscribeOn(Schedulers.io());

        withPriority(online, local).observeOn(Schedulers.computation()).subscribe(new Observer<String>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onNext(String s) {
                print("from:" + Thread.currentThread() + " value:" + s);
            }

            @Override
            public void onError(Throwable e) {
                print("onError from:" + Thread.currentThread() + " e:" + e);
            }

            @Override
            public void onComplete() {
                print("onComplete from:" + Thread.currentThread());
            }
        });
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static class PriorityObservable<T> extends Observable<T> implements Disposable {

        Observable<T>[] sources;
        InnerObserver<T>[] observers;
        Observer<? super T> observer;

        PriorityObservable(Observable<T>... sources) {
            this.sources = sources;
            observers = new InnerObserver[sources.length];
        }

        @Override
        protected void subscribeActual(Observer<? super T> observer) {
            this.observer = observer;
            observer.onSubscribe(this);
            for (int i = 0; i < sources.length; i++) {
                Observable<T> source = sources[i];
                source.subscribe(observers[i] = new InnerObserver<>(source, this));
            }
        }

        @Override
        public void dispose() {
            for (InnerObserver innerObserver : observers) {
                innerObserver.disposable.dispose();
            }
        }

        @Override
        public boolean isDisposed() {
            return observers[0].disposable.isDisposed();
        }

        private static class InnerObserver<T> implements Observer<T> {

            Observable<T> source;
            Disposable disposable;
            PriorityObservable<T> downStream;

            InnerObserver(Observable<T> source, PriorityObservable<T> downStream) {
                this.source = source;
                this.downStream = downStream;
            }

            @Override
            public void onSubscribe(Disposable d) {
                disposable = d;
            }

            @Override
            public void onNext(T t) {
                boolean disposeOthers = false;
                for (InnerObserver<T> innerObserver : downStream.observers) {
                    print("this:" + this + " it:" + innerObserver);
                    if (this == innerObserver) {
                        disposeOthers = true;
                    } else if (disposeOthers) {
                        innerObserver.disposable.dispose();
                    }
                }
                downStream.observer.onNext(t);
            }

            @Override
            public void onError(Throwable e) {
                if (!disposable.isDisposed()) {
                    downStream.observer.onError(e);
                }
            }

            @Override
            public void onComplete() {
                if (downStream.observers[0] == this) {
                    downStream.observer.onComplete();
                }
            }
        }
    }

    private <T> Observable<T> withPriority(Observable<T> high, Observable<T> low) {
        return new PriorityObservable<>(high, low);
    }

    public static class FormatCalendar {
        Calendar calendar;
        boolean is24Hour;

        FormatCalendar(Calendar calendar, boolean is24Hour) {
            this.calendar = calendar;
            this.is24Hour = is24Hour;
        }

        @Override
        public String toString() {
            return "FormatCalendar{" +
                    "calendar=" + calendar +
                    ", is24Hour=" + is24Hour +
                    '}';
        }
    }

    private static class SimpleConverter implements Converter<byte[], FormatCalendar> {

        @Override
        public FormatCalendar convert(byte[] value) {
            return null;
        }
    }

    private static class CalendarConverter implements /*Converter<byte[], FormatCalendar>*/TwoWayConverter<byte[], FormatCalendar> {

        private static final int TIME_ARRAY_LENGTH = 8;
        private static final int YEAR_H_INDEX = 0;
        private static final int YEAR_L_INDEX = 1;
        private static final int MONTH_INDEX = 2;
        private static final int DAY_INDEX = 3;
        private static final int HOUR_INDEX = 4;
        private static final int MINUTE_INDEX = 5;
        private static final int SECOND_INDEX = 6;
        private static final int TIME_FORMAT_INDEX = 7;
        private static final int DEFAULT_BYTE = 8;
        private static final int DEFAULT_LOW = 0x00FF;
        private static final int DEFAULT_PICK = 0xFF;
        private static final byte DEFAULT_HIGH = 0x00;

        private int formatByte2Int(byte highByte, byte lowByte) {
            int result = (highByte << DEFAULT_BYTE) | (DEFAULT_LOW & lowByte);
            return result;
        }

        private byte formatInt2Byte(int i) {
            byte result = (byte) (i & DEFAULT_PICK);
            return result;
        }

        @Override
        public FormatCalendar fromCar2App(byte[] time) {
            int year = formatByte2Int(time[YEAR_H_INDEX], time[YEAR_L_INDEX]);
            int month = formatByte2Int(DEFAULT_HIGH, time[MONTH_INDEX]);
            int day = formatByte2Int(DEFAULT_HIGH, time[DAY_INDEX]);
            int hour = formatByte2Int(DEFAULT_HIGH, time[HOUR_INDEX]);
            int minute = formatByte2Int(DEFAULT_HIGH, time[MINUTE_INDEX]);
            int second = formatByte2Int(DEFAULT_HIGH, time[SECOND_INDEX]);
            int timeFormat = formatByte2Int(DEFAULT_HIGH, time[TIME_FORMAT_INDEX]);

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month - 1);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, second);

            return new FormatCalendar(calendar, timeFormat == 1);
        }

        @Override
        public byte[] fromApp2Car(FormatCalendar formatCalendar) {
            byte yearH = (byte) (formatCalendar.calendar.get(Calendar.YEAR) >> DEFAULT_BYTE);
            byte yearL = (byte) (formatCalendar.calendar.get(Calendar.YEAR));
            byte month = formatInt2Byte(formatCalendar.calendar.get(Calendar.MONTH) + 1);
            byte day = formatInt2Byte(formatCalendar.calendar.get(Calendar.DAY_OF_MONTH));
            byte hour = formatInt2Byte(formatCalendar.calendar.get(Calendar.HOUR_OF_DAY));
            byte minute = formatInt2Byte(formatCalendar.calendar.get(Calendar.MINUTE));
            byte second = formatInt2Byte(formatCalendar.calendar.get(Calendar.SECOND));
            byte format;
            if (formatCalendar.is24Hour) {
                format = formatInt2Byte(1);
            } else {
                format = formatInt2Byte(0);
            }

            return new byte[]{yearH, yearL, month, day, hour, minute, second, format};
        }

//        @Override
//        public FormatCalendar convert(byte[] value) {
//            return fromCar2App(value);
//        }

//        @Override
//        public byte[] convert(FormatCalendar value) {
//            return fromApp2Data(value);
//        }
    }

    static TestCarApi api;

    private static void print(String msg) {
        System.out.println(msg);
    }

    private static class Integer2Boolean implements Converter<Integer, Boolean> {

        static Converter<Integer, Boolean> testC = value -> value != null;

        @Override
        public Boolean convert(Integer value) {
            return value < 0;
        }
    }

    @BeforeClass
    public static void start() {
        CarRetrofit.setDefault(new CarRetrofit.Builder()
                .addDataSource(new TestCarManager())
                .addConverter(new CalendarConverter())
                .addConverter(new ConverterImpl())
                .addConverter(new TwoWayConverter<Integer, MappedData>() {
                    @Override
                    public MappedData fromCar2App(Integer value) {
                        return new MappedData(value);
                    }

                    @Override
                    public Integer fromApp2Car(MappedData mappedData) {
                        return mappedData.rawData;
                    }
                })
                .addConverter(new Integer2Boolean())
                .build());
        api = CarRetrofit.fromDefault(TestCarApi.class);

//        carRetrofit.obtainConverterScope("test2")
//                .addConverter(new Converter<Integer, Boolean>() {
//                    @Override
//                    public Boolean convert(Integer value) {
//                        return value % 2 != 0;
//                    }
//                });
//
//
//        api = carRetrofit.obtainConverterScope("test")
//                .addConverter(new Converter<Integer, Boolean>() {
//                    @Override
//                    public Boolean convert(Integer value) {
//                        return value % 2 == 0;
//                    }
//                })
//                .create(TestCarApi.class);

//        api = carRetrofit.create(TestCarApi.class);

//        Function<String, String> inner = new Function<String, String>() {
//            @Override
//            public String apply(String s) {
//                return s;
//            }
//        };
//
    }

    @AfterClass
    public static void end() {
        try {
            Thread.sleep(200 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testClassImpl() {
    }

    @Test
    public void testJava() {
//        print("out: 0x" + Integer.toHexString(b & a));
    }

    @Test
    public void setGetWithConverter() {
        FormatCalendar calendar = new FormatCalendar(Calendar.getInstance(), new Random().nextBoolean());
        api.setByteArraySignal(calendar);

        print("calendar from api:" + api.getByteArraySignal());
    }

    @Test
    public void setGetInt() {
        print("setGetInt");
        int value = api.getIntSignal();
        print("before int value:" + value);
        api.setIntSignal(100);
        print("after int value:" + api.getIntSignal());
    }

    @Test
    public void setGetIntArray() {
        int[] intArray = api.getIntArraySignal();
        print("before int array value:" + Arrays.toString(intArray));
        api.setIntArraySignal(new int[] {7, 8, 9, 1000, 4324524});
        intArray = api.getIntArraySignal();
        print("after int array value:" + Arrays.toString(intArray));
    }

    @Test
    public void setGetString() {
        print("before string value:" + api.getStringSignal());
        api.setStringSignal("set a string value");
        print("after string value:" + api.getStringSignal());
    }

    @Test
    public void setGetStringArray() {
        print("before string array value:" + Arrays.toString(api.getStringArraySignal()));
        api.setStringArraySignal(new String[]{"what", "ever", "string", "is"});
        print("after string array value:" + Arrays.toString(api.getStringArraySignal()));
    }

    @Test
    public void setGetByte() {
        print("before byte value:" + api.getByteSignal());
        api.setByteSignal(Byte.MIN_VALUE);
        print("after byte value:" + api.getByteSignal());
    }

    @Test
    public void setBuildInValue() {
        api.set6IntValue();
        api.set9StringArrayValue();
        print("after build in set 6 " + api.get6IntValue());
        print("after build in set 9 " + Arrays.toString(api.get9StringArrayValue()));
    }

    @Test
    public void injectAndApply() {
//        CarData data = new CarData();
//        api.injectComboData(data);
        print("old array:" + Arrays.toString(api.get9StringArrayValue()));
//        CarData data = api.getComboData();
        api.applyComboValues(new CarData());
        print(/*"before extract data:" + data +*/ " array:" + Arrays.toString(api.get9StringArrayValue()));

//        api.applyComboValues(new CarData());
//
//        data = new CarData();
//        api.injectComboData(data);
//        print("after apply data:" + data);
    }

    @Test
    public void injectAndApplyByRetrofitInstance() {
//        CarData data = new CarData();
//        carRetrofit.inject(data);
////        CarData data = api.getComboData();
//        print("before extract data:" + data);
//
//        carRetrofit.apply(new CarData());
//
//        data = new CarData();
//        carRetrofit.inject(data);
//        print("after apply data:" + data);
    }

    @Test
    public void converterScopeTest() {
//        api.trackIntReactive().subscribe(new Consumer<Integer>() {
//            @Override
//            public void accept(Integer integer) throws Exception {
//                print("int accept:" + integer);
//            }
//        });
//        api.trackIntReactiveAlias().subscribe(new Consumer<Integer>() {
//            @Override
//            public void accept(Integer integer) throws Exception {
//                print("int delegate accept:" + integer);
//            }
//        });
//        api.trackBooleanReactive().subscribe(new Consumer<Boolean>() {
//            @Override
//            public void accept(Boolean aBoolean) throws Exception {
//                print("boolean accept:" + aBoolean);
//            }
//        });
        CarRetrofit.fromDefault(TestChildCarApi.class)
                .trackIntReactiveAlias()
                .subscribe(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) throws Exception {
                print("child int accept:" + integer);
            }
        });
//        api.setIntSignal(10086);
//        api.trackIntSignal().addObserver(new java.util.function.Consumer<Integer>() {
//            @Override
//            public void accept(Integer value) {
//                print("flow accept:" + value);
//            }
//        });
//        carRetrofit.obtainConverterScope("test_scope").addConverter(new w)
//        CarData data = new CarData();
//        carRetrofit.inject(data);
////        CarData data = api.getComboData();
//        print("before extract data:" + data);
//
//        carRetrofit.apply(new CarData());
//
//        data = new CarData();
//        carRetrofit.inject(data);
//        print("after apply data:" + data);
    }

    public static class MappedData {
        int rawData;

        MappedData(int rawData) {
            this.rawData = rawData;
        }

        @Override
        public String toString() {
            return "MappedData{" +
                    "rawData=" + rawData +
                    '}';
        }
    }

    public static class MapConverter implements TwoWayConverter<MappedData, Integer> {
        @Override
        public Integer fromCar2App(MappedData value) {
            return value.rawData;
        }

        @Override
        public MappedData fromApp2Car(Integer value) {
            return new MappedData(value);
        }

//        @Override
//        public MappedData fromCar2App(Integer value) {
//            return new MappedData(value);
//        }
//
//        @Override
//        public Integer fromApp2Data(MappedData mappedData) {
//            return mappedData.rawData;
//        }
    }

    @Test
    public void combineTest() {

//        api.trackStringAndCombine().subscribe(new Consumer<String>() {
//            @Override
//            public void accept(String string) throws Exception {
//                print("combine receive:" + string);
//            }
//        });
    }

    @Test
    public void combineDelegate() {
        api.trackIntAndBoolean().subscribe(new Consumer<String>() {
            @Override
            public void accept(String string) throws Exception {
                print("combine accept:" + string);
            }
        });

//        api.trackStringAndCombine().subscribe(new Consumer<String>() {
//            @Override
//            public void accept(String string) throws Exception {
//                print("combine receive:" + string);
//            }
//        });
//            api.trackIntDelegate().subscribe(new Consumer<String>() {
//            @Override
//            public void accept(String value) throws Exception {
//                print("delegate accept:" + value);
//            }
//        });
    }

    @Test
    public void track() {

        ObservableBoolean observableBoolean = api.trackIntMappedReactive();
        observableBoolean.addOnPropertyChangedCallback(
                new androidx.databinding.Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(androidx.databinding.Observable sender, int propertyId) {
                print("changed value:" + observableBoolean.get());
            }
        });
//        api.trackIntMappedReactive().addOnPropertyChangedCallback(new androidx.databinding.Observable.OnPropertyChangedCallback() {
//            @Override
//            public void onPropertyChanged(androidx.databinding.Observable sender, int propertyId) {
//                print("changed value:" + observableBoolean.get());
//            }
//        });
//        api.trackCustomFlow().addConsumer(new java.util.function.Consumer<Boolean>() {
//            @Override
//            public void accept(Boolean aBoolean) {
//                print("test:" + aBoolean);
//            }
//        });

//        api.trackIntMappedReactive()
//                .subscribe(new Observer<MappedData>() {
//                    @Override
//                    public void onSubscribe(Disposable d) {
//                        print("onSubscribe d:" + d.getClass());
//                    }
//
//                    @Override
//                    public void onNext(MappedData data) {
//                        print("onNext integer:" + data + " from:" + Thread.currentThread());
//                    }
//
//                    @Override
//                    public void onError(Throwable e) {
//                        print("onError e:" + e);
//                    }
//
//                    @Override
//                    public void onComplete() {
//                        print("onComplete");
//                    }
//                });

//        try {
//            Thread.sleep(20 * 1000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
    }

//    @InjectSuper
//    @ApplySuper
    public static class BaseData {
//        @Set(key = 6)
        @Get(id = 6)
        int baseAbc = 4321;
    }

    @CarApi(scope = TestCarManager.class)
    public static class InnerData extends BaseData {
        @Get(id = 1)
        @Set(id = 1)
        private int[] aaa = {10086};

        @Get(id = 3)
        @Set(id = 3)
        private String[] bbb = {"inner", "set"};

        @Get(id = 5)
        @Set(id = 5)
        FormatCalendar calendar;

        {
            calendar = new FormatCalendar(Calendar.getInstance(Locale.CANADA), false);
            calendar.calendar.set(1964, 4, 13);
        }

        @Override
        public String toString() {
            return "InnerData{" +
                    "aaa=" + Arrays.toString(aaa) +
                    ", bbb=" + Arrays.toString(bbb) +
                    ", calendar=" + calendar +
                    ", baseAbc=" + baseAbc +
                    '}';
        }
    }

    public static class CarData {
        @Delegate(TestCarApiId.get6IntValue)
        private int abc = 151;

        @Delegate(TestCarApiId.get7IntValue)
        int[] abcd = {1, 2, 3, 4, 100000000};

        @Delegate(TestCarApiId.get8StringValue)
        String cvb = "hello bird";

        @Delegate(TestCarApiId.setStringArraySignal)
        String[] vbns = {"hello", "another", "flying", "bird"};

        @Override
        public String toString() {
            return "CarData{" +
                    "abc=" + abc +
                    ", abcd=" + Arrays.toString(abcd) +
                    ", cvb='" + cvb + '\'' +
                    ", vbns=" + Arrays.toString(vbns) +
                    ", firstOb=" + firstOb +
//                    ", data=" + data +
                    '}';
        }

        @Delegate(TestCarApiId.trackIntReactive)
        Observable<Integer> firstOb;

//        @Inject
//        @Apply
//        InnerData data = new InnerData();
    }
}