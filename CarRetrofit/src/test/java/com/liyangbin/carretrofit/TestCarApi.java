package com.liyangbin.carretrofit;

import androidx.databinding.ObservableBoolean;

import com.liyangbin.carretrofit.annotation.CarApi;
import com.liyangbin.carretrofit.annotation.Register;
import com.liyangbin.carretrofit.annotation.CarValue;
import com.liyangbin.carretrofit.annotation.Combine;
import com.liyangbin.carretrofit.annotation.Delegate;
import com.liyangbin.carretrofit.annotation.Get;
import com.liyangbin.carretrofit.annotation.Inject;
import com.liyangbin.carretrofit.annotation.Set;
import com.liyangbin.carretrofit.annotation.Track;
import com.liyangbin.carretrofit.annotation.UnTrack;

import io.reactivex.Observable;
import io.reactivex.Single;

import static com.liyangbin.carretrofit.TestCarApiId.*;

@CarApi(scope = "test")
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

    @Track(id = 2, sticky = StickyType.ON)
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

    @Track(id = 0, restoreSet = setIntSignal)
    Observable<Integer> trackIntReactive();

    @Delegate(trackIntReactive)
    Observable<Integer> trackIntReactiveAlias();

    @Delegate(trackStringAndCombine)
    Observable<String> trackIntDelegate();

    @Track(id = 0, sticky = StickyType.ON)
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

    @Register
    void register2Callback(MyCallback callback);

    @UnTrack(track = register2Callback)
    void unregisterCallback(MyCallback callback);
}
