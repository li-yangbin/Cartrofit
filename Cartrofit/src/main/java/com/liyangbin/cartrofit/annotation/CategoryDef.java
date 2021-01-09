package com.liyangbin.cartrofit.annotation;

import androidx.annotation.IntDef;

@IntDef(value = {Category.CATEGORY_SET, Category.CATEGORY_GET,
        Category.CATEGORY_TRACK, Category.CATEGORY_DEFAULT}, flag = true)
@interface CategoryDef {
}
