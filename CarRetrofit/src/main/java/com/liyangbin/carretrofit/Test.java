package com.liyangbin.carretrofit;

import android.car.hardware.property.CarPropertyManager;

import com.liyangbin.carretrofit.annotation.CarApi;
import com.liyangbin.carretrofit.annotation.Get;
import com.liyangbin.carretrofit.annotation.ProcessSuper;
import com.liyangbin.carretrofit.annotation.Set;

import java.util.ArrayList;

@CarApi
public interface Test {

    @Get(id = 123)
    int getIntSignal(int a, byte afsdc, ArrayList<String> list1, Object aaa);

    @Set(id = 123)
    void setIntSignal(int value);

    @Set(id = 0)
    void setIntSignal11(Object value, String str, int[] test, ArrayList<String> list);
}

//@CarApi
//interface ITest {
//    @Get(id = 123)
//    int getIntSignal(int a, byte afsdc, ArrayList<String> list1, Object aaa);
//
//    @Set(id = 123)
//    void setIntSignal(int value);
//
//    @Set(id = 0)
//    void setIntSignal(Object value, String str, int[] test, ArrayList<String> list);
//}
//
//interface CustomTest {
//    int asfgsdsg(int a);
//
//    void shtrsh(String value);
//}
