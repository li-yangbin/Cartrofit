package com.liyangbin.cartrofit.funtion;

public class Union5<T1, T2, T3, T4, T5> extends Union4<T1, T2, T3, T4> {

    static Union5<?, ?, ?, ?, ?> sPool5;
    static int sSize5;

    public T5 value5;

    Union5(T1 value1, T2 value2, T3 value3, T4 value4, T5 value5) {
        super(value1, value2, value3, value4);
        this.value5 = value5;
    }

    @Override
    public int getCount() {
        return 5;
    }

    @Override
    public Object get(int index) {
        return index == 4 ? value5 : super.get(index);
    }

    @Override
    public void recycle() {
        value1 = null;
        value2 = null;
        value3 = null;
        value4 = null;
        value5 = null;

        synchronized (Union5.class) {
            if (sSize5 < LIMIT) {
                next = sPool5;
                sPool5 = this;
                sSize5++;
            }
        }
    }
}