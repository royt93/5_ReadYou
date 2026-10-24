package com.mckimquyen.reader.domain.repository

/** Port để application layer báo widget metadata đã đổi mà domain không phụ thuộc RemoteViews. */
fun interface WidgetRefreshNotifier {
    fun refresh()
}
