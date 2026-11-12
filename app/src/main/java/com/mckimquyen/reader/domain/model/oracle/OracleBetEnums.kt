package com.mckimquyen.reader.domain.model.oracle

enum class OracleBetSide { YES, NO }

/** OPEN: stake taken, result unknown; WON/LOST/REFUNDED are final. */
enum class OracleBetStatus { OPEN, WON, LOST, REFUNDED }

/** What the user reports once the prediction's date has passed. UNCLEAR returns the stake. */
enum class OracleOutcome { YES, NO, UNCLEAR }
