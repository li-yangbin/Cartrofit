package com.liyangbin.cartrofit.annotation;

import java.lang.annotation.Retention;

import androidx.annotation.IntDef;

import static java.lang.annotation.RetentionPolicy.SOURCE;

@IntDef(value = {ParameterCategory.INIT, ParameterCategory.ACCUMULATE, ParameterCategory.EXTRA})
@Retention(SOURCE)
@interface ParameterCategoryDef {
}
