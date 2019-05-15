package org.mozilla.fenix.ext

fun isDeviceVersionNOrLater() =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N
