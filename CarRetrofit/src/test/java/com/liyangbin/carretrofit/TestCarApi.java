package com.liyangbin.carretrofit;

import com.liyangbin.carretrofit.annotation.Apply;
import com.liyangbin.carretrofit.annotation.CarApi;
import com.liyangbin.carretrofit.annotation.CarValue;
import com.liyangbin.carretrofit.annotation.Combine;
import com.liyangbin.carretrofit.annotation.Get;
import com.liyangbin.carretrofit.annotation.Inject;
import com.liyangbin.carretrofit.annotation.Set;
import com.liyangbin.carretrofit.annotation.Track;
import com.liyangbin.carretrofit.funtion.Function2;

import androidx.databinding.ObservableBoolean;
import io.reactivex.Observable;
import io.reactivex.Single;

@CarApi(scope = "test")
public interface TestCarApi {

//    Interceptor INTERCEPTOR = (command, parameter) -> {
//        System.out.println("before process command:" + command + " parameter:" + parameter);
//        Object obj = command.invoke(parameter);
//        System.out.println("after process command:" + command + " parameter:" + parameter);
//        return obj;
//    };

    @Get(id = 0)
    int getIntSignal();

    @Set(id = 0)
    void setIntSignal(int value);

    @Get(id = 1)
    int[] getIntArraySignal();

    @Set(id = 1)
    void setIntArraySignal(int[] value);

    @Get(id = 2)
    String getStringSignal();

    @Set(id = 2)
    void setStringSignal(String value);

    @Get(id = 3)
    String[] getStringArraySignal();

    @Set(id = 3)
    void setStringArraySignal(String[] value);

    @Get(id = 4)
    byte getByteSignal();

    @Set(id = 4)
    void setByteSignal(byte value);

    @Get(id = 5)
    ExampleUnitTest.FormatCalendar getByteArraySignal();

    @Get(id = 5)
    byte[] getRawByteArray();

    @Set(id = 5)
    void setByteArraySignal(ExampleUnitTest.FormatCalendar value);

    @Track(id = 0)
    Flow<Integer> trackIntSignal();

    @Track(id = 0, sticky = StickyType.ON)
    Observable<Integer> trackIntReactive();

    @Track(id = 0, sticky = StickyType.ON)
    Observable<Boolean> trackBooleanReactive();

    Function2<Integer, Boolean, String> combinator_aa = new Function2<Integer, Boolean, String>() {

        @Override
        public String apply(Integer value1, Boolean value2) {
            return value1 + " assemble " + value2;
        }
    };

    @Combine(elements = {"trackIntReactive", "trackBooleanReactive"}, combinator = "combinator_aa")
    Observable<String> trackIntAndBoolean();

    @Track(id = 0)
    ObservableBoolean trackIntMappedReactive();

    @Track(id = 0)
    Single<Integer> trackIntReactiveSingle();

    @Track(id = 0)
    MyFlow trackCustomFlow();

//    @MultiSet(set = {@Set(key = 6, token = "six"),
//                    @Set(key = 7, token = "seven"),
//                    @Set(key = 8, token = "eight"),
//                    @Set(key = 9, token = "nine")})
//    void set6789Values(@Param(token = "seven") int[] seven,
//                       @Param(token = "six") int six,
//                       @Param(token = "nine") String[] nine,
//                       @Param(token = "eight") String eight);
//
//    @MultiSet(set = {@Set(key = 6, token = "abc"),
//            @Set(key = 7, token = "abcd"),
//            @Set(key = 8, token = "cvb"),
//            @Set(key = 9, token = "vbns")})
//    void set6789ComboValues(@MultiParam ExampleUnitTest.CarData cardata);

    @Apply
    void applyComboValues(ExampleUnitTest.CarData cardata);

    @Get(id = 6)
    int get6IntValue();

    @Get(id = 7)
    int[] get7IntValue();

    @Get(id = 8)
    String get8StringValue();

    @Get(id = 9)
    String[] get9StringArrayValue();

    @Set(id = 6, value = @CarValue(Int = 10086))
    void set6IntValue();

    @Set(id = 9, value = @CarValue(stringArray = {"build", "in", "set"}))
    void set9StringArrayValue();

    @Inject
    ExampleUnitTest.CarData getComboData();

    @Inject
    ExampleUnitTest.CarData getComboData2(ExampleUnitTest.CarData data);

    @Inject
    void injectComboData(ExampleUnitTest.CarData data);
}
