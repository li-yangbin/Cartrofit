package com.liyangbin.cartrofit.annotation;

import java.lang.annotation.Retention;

import androidx.annotation.IntDef;

import static java.lang.annotation.RetentionPolicy.SOURCE;

@IntDef(value = {MethodCategory.CATEGORY_SET, MethodCategory.CATEGORY_GET,
        MethodCategory.CATEGORY_TRACK, MethodCategory.CATEGORY_DEFAULT}, flag = true)
@Retention(SOURCE)
@interface MethodCategoryDef {
}
