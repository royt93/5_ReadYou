package com.mckimquyen.reader

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Chỉ dành cho test UI E-Ink không cần Hilt; không khởi tạo SDK quảng cáo hoặc đồng bộ nền. */
class EInkUiTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
