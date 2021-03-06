package com.liyangbin.cartrofit.solution;

import java.lang.annotation.Annotation;

interface AbsAccumulator<A extends Annotation, V, R> {
    R advance(A annotation, R old, V para);
}
