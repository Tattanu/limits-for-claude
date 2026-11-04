package com.loisebastiano.claudelimits

import android.os.Build
import android.view.View
import android.view.WindowInsets

/** Android 15+ draws apps edge-to-edge: keep content clear of status bar, navigation bar and keyboard. */
fun View.padForSystemBars(extra: Int = 0) {
    setOnApplyWindowInsetsListener { v, insets ->
        if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(i.left + extra, i.top + extra, i.right + extra, i.bottom + extra)
        } else {
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft + extra, insets.systemWindowInsetTop + extra,
                insets.systemWindowInsetRight + extra, insets.systemWindowInsetBottom + extra)
        }
        insets
    }
    requestApplyInsets()
}
