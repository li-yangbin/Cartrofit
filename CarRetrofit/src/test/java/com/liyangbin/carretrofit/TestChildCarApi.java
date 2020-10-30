package com.liyangbin.carretrofit;

import com.liyangbin.carretrofit.annotation.CarApi;
import com.liyangbin.carretrofit.annotation.Delegate;

import io.reactivex.Observable;

@CarApi
public interface TestChildCarApi {

//    @Delegate(getIntSignal)
    int getIntValueAlias();

    @Delegate(TestCarApiId.trackIntReactive)
    Observable<Integer> trackIntReactiveAlias();
}
