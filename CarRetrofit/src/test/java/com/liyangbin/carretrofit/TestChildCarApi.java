package com.liyangbin.carretrofit;

import com.liyangbin.carretrofit.annotation.CarApi;
import com.liyangbin.carretrofit.annotation.Delegate;

import io.reactivex.Observable;

import static com.liyangbin.carretrofit.TestCarApiId.*;

@CarApi
public interface TestChildCarApi {

    @Delegate(getIntSignal)
    int getIntValueAlias();

    @Delegate(trackIntReactive)
    Observable<Integer> trackIntReactiveAlias();
}
