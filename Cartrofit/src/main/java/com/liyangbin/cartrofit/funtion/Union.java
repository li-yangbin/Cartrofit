package com.liyangbin.cartrofit.funtion;

import com.liyangbin.cartrofit.CartrofitGrammarException;

import java.util.Arrays;

@SuppressWarnings("unchecked")
public class Union<T> {
    private static final int LIMIT = 5;
    static Union<?> sPool;
    static int sSize;

    public T value1;

    Union<?> next;

    Union(T t) {
        value1 = t;
    }

    public int getCount() {
        return 1;
    }

    public Object get(int index) {
        if (index != 0) {
            throw new IndexOutOfBoundsException("size:" + getCount() + " index:" + index);
        }
        return value1;
    }

    void recycle() {
        value1 = null;

        synchronized (Union.class) {
            if (sSize < LIMIT) {
                next = sPool;
                sPool = this;
                sSize++;
            }
        }
    }

    public static Union<?> of(Object[] array) {
        if (array == null || array.length == 0) {
            return null;
        }
        switch (array.length) {
            case 1:
                synchronized (Union.class) {
                    if (Union.sPool == null) {
                        return new Union<>(array[0]);
                    }
                    Union<Object> out = (Union<Object>) Union.sPool;
                    out.value1 = array[0];

                    Union.sPool = out.next;
                    Union.sSize--;
                    return out;
                }
            case 2:
                return of(array[0], array[1]);
            case 3:
                return of(array[0], array[1], array[2]);
            case 4:
                return of(array[0], array[1], array[2], array[3]);
            case 5:
                return of(array[0], array[1], array[2], array[3], array[4]);
            default:
                throw new CartrofitGrammarException("Invalid input array:" + Arrays.toString(array));
        }
    }

    public static <T1, T2> Union2<T1, T2> of(T1 t1, T2 t2) {
        synchronized (Union2.class) {
            if (Union2.sPool2 == null) {
                return new Union2<>(t1, t2);
            }
            Union2<T1, T2> out = (Union2<T1, T2>) Union2.sPool2;
            out.value1 = t1;
            out.value2 = t2;

            Union2.sPool2 = (Union2<?, ?>) out.next;
            Union2.sSize2--;
            return out;
        }
    }

    public static <T1, T2, T3> Union3<T1, T2, T3> of(T1 t1, T2 t2, T3 t3) {
        synchronized (Union3.class) {
            if (Union3.sPool3 == null) {
                return new Union3<>(t1, t2, t3);
            }
            Union3<T1, T2, T3> out = (Union3<T1, T2, T3>) Union3.sPool3;
            out.value1 = t1;
            out.value2 = t2;
            out.value3 = t3;

            Union3.sPool3 = (Union3<?, ?, ?>) out.next;
            Union3.sSize3--;
            return out;
        }
    }

    public static <T1, T2, T3, T4> Union4<T1, T2, T3, T4> of(T1 t1, T2 t2, T3 t3, T4 t4) {
        synchronized (Union4.class) {
            if (Union4.sPool4 == null) {
                return new Union4<>(t1, t2, t3, t4);
            }
            Union4<T1, T2, T3, T4> out = (Union4<T1, T2, T3, T4>) Union4.sPool4;
            out.value1 = t1;
            out.value2 = t2;
            out.value3 = t3;
            out.value4 = t4;

            Union4.sPool4 = (Union4<?, ?, ?, ?>) out.next;
            Union4.sSize4--;
            return out;
        }
    }

    public static <T1, T2, T3, T4, T5> Union5<T1, T2, T3, T4, T5> of(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) {
        synchronized (Union5.class) {
            if (Union5.sPool5 == null) {
                return new Union5<>(t1, t2, t3, t4, t5);
            }
            Union5<T1, T2, T3, T4, T5> out = (Union5<T1, T2, T3, T4, T5>) Union5.sPool5;
            out.value1 = t1;
            out.value2 = t2;
            out.value3 = t3;
            out.value4 = t4;
            out.value5 = t5;

            Union5.sPool5 = (Union5<?, ?, ?, ?, ?>) out.next;
            Union5.sSize5--;
            return out;
        }
    }

    public static class Union2<T1, T2> extends Union<T1> {

        static Union2<?, ?> sPool2;
        static int sSize2;

        public T2 value2;

        Union2(T1 value1, T2 value2) {
            super(value1);
            this.value2 = value2;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Object get(int index) {
            return index == 1 ? value2 : super.get(index);
        }

        @Override
        public void recycle() {
            value1 = null;
            value2 = null;

            synchronized (Union2.class) {
                if (sSize2 < LIMIT) {
                    next = sPool2;
                    sPool2 = this;
                    sSize2++;
                }
            }
        }
    }

    public static class Union3<T1, T2, T3> extends Union2<T1, T2> {

        static Union3<?, ?, ?> sPool3;
        static int sSize3;

        public T3 value3;

        Union3(T1 value1, T2 value2, T3 value3) {
            super(value1, value2);
            this.value3 = value3;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public Object get(int index) {
            return index == 2 ? value3 : super.get(index);
        }

        @Override
        public void recycle() {
            value1 = null;
            value2 = null;
            value3 = null;

            synchronized (Union3.class) {
                if (sSize3 < LIMIT) {
                    next = sPool3;
                    sPool3 = this;
                    sSize3++;
                }
            }
        }
    }

    public static class Union4<T1, T2, T3, T4> extends Union3<T1, T2, T3> {

        static Union4<?, ?, ?, ?> sPool4;
        static int sSize4;

        public T4 value4;

        Union4(T1 value1, T2 value2, T3 value3, T4 value4) {
            super(value1, value2, value3);
            this.value4 = value4;
        }

        @Override
        public int getCount() {
            return 4;
        }

        @Override
        public Object get(int index) {
            return index == 3 ? value4 : super.get(index);
        }

        @Override
        public void recycle() {
            value1 = null;
            value2 = null;
            value3 = null;
            value4 = null;

            synchronized (Union4.class) {
                if (sSize4 < LIMIT) {
                    next = sPool4;
                    sPool4 = this;
                    sSize4++;
                }
            }
        }
    }

    public static class Union5<T1, T2, T3, T4, T5> extends Union4<T1, T2, T3, T4> {

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
}
