package com.mckimquyen.reader.domain.sv

/** Kết quả của một yêu cầu đọc tin rảnh tay. */
enum class ReadNewsOutcome { STARTED, NO_UNREAD, NO_READABLE_TEXT, QUEUE_FINISHED, ERROR }
