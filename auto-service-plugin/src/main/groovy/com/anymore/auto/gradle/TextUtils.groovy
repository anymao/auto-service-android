package com.anymore.auto.gradle

import javax.annotation.Nullable;

/**
 * Created by anymore on 2022/5/19.
 */
class TextUtils {
    static boolean isEmpty(@Nullable String s) {
        return s == null || s.length() == 0
    }
}
