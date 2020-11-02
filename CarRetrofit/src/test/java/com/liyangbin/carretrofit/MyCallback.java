package com.liyangbin.carretrofit;

import com.liyangbin.carretrofit.annotation.CarApi;
import com.liyangbin.carretrofit.annotation.Delegate;
import com.liyangbin.carretrofit.annotation.Out;
import com.liyangbin.carretrofit.annotation.Set;
import com.liyangbin.carretrofit.annotation.Track;

import java.util.Arrays;

import static com.liyangbin.carretrofit.TestCarApiId.setIntSignal;
import static com.liyangbin.carretrofit.TestCarApiId.setStringArraySignal;

@CarApi(scope = "test")
public interface MyCallback {

    @Delegate(TestCarApiId.trackStringSignal)
    void onStringChange(String aa);

    @Track(id = 2)
    void onStringSignalChange(String bb);

//    @Track(id = 0)
//    @Set(id = 2)String onIntSignalChange(int cc);

    @Track(id = 2)
    void loadStringArray(@Out StringArrayRespond respond, String bbb);

    @Delegate(TestCarApiId.trackIntReactive)
    void onIntSignalChangeDele(int d);
}

class StringArrayRespond implements ApplyReceiver {

    @Delegate(setStringArraySignal)
    String[] stringArray;

    @Delegate(setIntSignal)
    int index;

    @Override
    public void onBeforeApply() {
        System.out.println("onBeforeApply this:" + this);
    }

    @Override
    public String toString() {
        return "StringArrayRespond{" +
                "stringArray=" + Arrays.toString(stringArray) +
                ", index=" + index +
                '}';
    }
}
