package com.liyangbin.carretrofit;

import com.liyangbin.carretrofit.annotation.CarApi;
import com.liyangbin.carretrofit.annotation.Get;

@CarApi(scope = "test")
public interface BaseTest {
    @Get(id = 0)
    int getIntSignal();
}
