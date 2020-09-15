package com.liyangbin.carretrofit;

import com.liyangbin.carretrofit.annotation.Get;
import com.liyangbin.carretrofit.annotation.ProcessSuper;
import com.liyangbin.carretrofit.annotation.Set;

import java.util.ArrayList;

@ProcessSuper(implementClass = ITest.class)
public class Test {
}

interface ITest {
    @Get(id = 0)
    int getIntSignal(int a, byte afsdc, ArrayList<String> list1, Object aaa);

    @Set(id = 0)
    void setIntSignal(int value);

    @Set(id = 0)
    void setIntSignal(Object value, String str, int[] test, ArrayList<String> list);
}

interface CustomTest {
    int asfgsdsg(int a);

    void shtrsh(String value);
}
