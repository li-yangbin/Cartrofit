package com.android.carretrofit;

import java.util.ArrayList;

@ProcessSuper(implementClass = ITest.class)
public class Test {
}

interface ITest {
    @Get(key = 0)
    int getIntSignal(int a, byte afsdc, ArrayList<String> list1, Object aaa);

    @Set(key = 0)
    void setIntSignal(int value);

    @Set(key = 0)
    void setIntSignal(Object value, String str, int[] test, ArrayList<String> list);
}

interface CustomTest {
    int asfgsdsg(int a);

    void shtrsh(String value);
}
