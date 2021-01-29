package com.liyangbin.cartrofit.carproperty;

import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.GenerateId;
import com.liyangbin.cartrofit.flow.Flow;

import io.reactivex.Observable;
import io.reactivex.Single;

import static com.liyangbin.cartrofit.carproperty.TestCarApiId.trackStringSignal;

@GenerateId
@Scope("test")
public interface TestCarApi {

    @Get(propId = 0)
    int getIntSignal();

    @Set(propId = 0)
    void setIntSignal(int value);

    @Get(propId = 1)
    int[] getIntArraySignal();

    @Set(propId = 1)
    void setIntArraySignal(int[] value);

    @Get(propId = 2)
    String getStringSignal();

    @Set(propId = 2)
    void setStringSignal(String value);

    @Track(propId = 2)
    Flow<String> trackStringSignal();

    @Get(propId = 3)
    String[] getStringArraySignal();

    @Set(propId = 3)
    void setStringArraySignal(String[] value);

    @Get(propId = 4)
    byte getByteSignal();

    @Set(propId = 4)
    void setByteSignal(byte value);

    @Get(propId = 5)
    byte[] getRawByteArray();

    @Track(propId = 1)
    Observable<Integer> trackIntReactive();

    @Delegate(trackStringSignal)
    Observable<String> trackIntReactiveAlias();

    @Track(propId = 1)
    Single<Integer> trackIntReactiveSingle();
}