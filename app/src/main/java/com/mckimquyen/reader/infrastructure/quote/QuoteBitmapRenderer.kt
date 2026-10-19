package com.mckimquyen.reader.infrastructure.quote

import android.graphics.Bitmap
import com.mckimquyen.reader.domain.model.quote.QuoteCardSpec
import com.mckimquyen.reader.domain.model.quote.QuoteCardTemplate

/** Cổng vẽ thẻ để ViewModel không phụ thuộc trực tiếp [QuoteCardRenderer] (suspend để test mô phỏng render chậm). */
fun interface QuoteBitmapRenderer {
    suspend fun render(spec: QuoteCardSpec, template: QuoteCardTemplate): Bitmap
}
