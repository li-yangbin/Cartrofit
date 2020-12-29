package com.liyangbin.cartrofit;

import androidx.databinding.ObservableBoolean;

import com.liyangbin.cartrofit.annotation.CarValue;
import com.liyangbin.cartrofit.annotation.Combine;
import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.GenerateId;
import com.liyangbin.cartrofit.annotation.Get;
import com.liyangbin.cartrofit.annotation.In;
import com.liyangbin.cartrofit.annotation.Inject;
import com.liyangbin.cartrofit.annotation.Out;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Set;
import com.liyangbin.cartrofit.annotation.Track;
import com.liyangbin.cartrofit.annotation.Unregister;

import io.reactivex.Observable;
import io.reactivex.Single;

import static com.liyangbin.cartrofit.TestCarApiId.register2Callback;
import static com.liyangbin.cartrofit.TestCarApiId.trackBooleanReactive;
import static com.liyangbin.cartrofit.TestCarApiId.trackIntAndBoolean;
import static com.liyangbin.cartrofit.TestCarApiId.trackIntReactive;
import static com.liyangbin.cartrofit.TestCarApiId.trackStringAndCombine;
import static com.liyangbin.cartrofit.TestCarApiId.trackStringSignal;

@GenerateId
@Scope("test")
public interface TestCarApi {

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

    @Track(id = 2)
    Observable<String> trackStringSignal();

    @Get(id = 3)
    String[] getStringArraySignal();

    @Set(id = 9)
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

    @Track(id = 0/*, restoreSet = setIntSignal*/)
    Observable<Integer> trackIntReactive();

    @Delegate(trackIntReactive)
    Observable<Integer> trackIntReactiveAlias();

    @Delegate(trackStringAndCombine)
    Observable<String> trackIntDelegate();

    @Track(id = 0)
    Observable<Boolean> trackBooleanReactive();

    @Combine(elements = {trackStringSignal, trackBooleanReactive})
    Observable<String> trackIntAndBoolean();

    @Combine(elements = {trackStringSignal, trackIntAndBoolean})
    Observable<String> trackStringAndCombine();

    @Track(id = 0)
    ObservableBoolean trackIntMappedReactive();

    @Track(id = 0)
    Single<Integer> trackIntReactiveSingle();

    @Track(id = 0)
    MyFlow trackCustomFlow();

    @Inject
    void applyComboValues(@In@Out ExampleUnitTest.CarData cardata);

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

    @Register
    void register2Callback(MyCallback callback);

    @Unregister(register2Callback)
    void unregisterCallback(MyCallback callback);
}