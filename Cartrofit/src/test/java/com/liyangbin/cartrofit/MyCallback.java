package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.In;
import com.liyangbin.cartrofit.annotation.Out;
import com.liyangbin.cartrofit.annotation.Track;

import java.util.Arrays;

import static com.liyangbin.cartrofit.TestCarApiId.setIntSignal;
import static com.liyangbin.cartrofit.TestCarApiId.setStringArraySignal;

public interface MyCallback {

    @Delegate(TestCarApiId.trackStringSignal)
    void onStringChange(String aa);

    @Track(id = 2)
    void onStringSignalChange(String bb);

//    @Track(id = 0)
//    @Set(id = 2)String onIntSignalChange(int cc);

    @Track(id = 2)
    void loadStringArray(@In ExampleUnitTest.CarData data, @Out StringArrayRespond respond, String bbb);

    @Delegate(TestCarApiId.trackIntReactive)
    void onIntSignalChangeDele(int d);
}

class StringArrayRespond implements InjectReceiver {

    @Delegate(setStringArraySignal)
    String[] stringArray;

    @Delegate(setIntSignal)
    int index;

    @Override
    public String toString() {
        return "StringArrayRespond{" +
                "stringArray=" + Arrays.toString(stringArray) +
                ", index=" + index +
                '}';
    }

    @Override
    public boolean onBeforeInject(Command command) {
        System.out.println("onBeforeInject from:" + this + " by:" + command);
        return false;
    }

    @Override
    public void onAfterInject(Command command) {

    }
}
