package com.liyangbin.carretrofit;

import com.liyangbin.carretrofit.annotation.CarApi;
import com.liyangbin.carretrofit.annotation.Delegate;
import com.liyangbin.carretrofit.annotation.Set;
import com.liyangbin.carretrofit.annotation.Track;

@CarApi(scope = "test")
public interface MyCallback {

    @Delegate(TestCarApiId.trackStringSignal)
    void onStringChange(String aa);

    @Track(id = 2)
    void onStringSignalChange(String bb);

    @Track(id = 0)
    @Set(id = 2)String onIntSignalChange(int cc);

    @Delegate(TestCarApiId.trackIntReactive)
    void onIntSignalChangeDele(int d);
}
