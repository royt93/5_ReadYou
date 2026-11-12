package com.mckimquyen.reader.domain.ai.agents

/** One AI-written Deep Dive (FRONT-01). Plain data: validated by [DeepDivePolicy.parseAndValidate] before it exists. */
data class DeepDiveReport(
    val articleId: String,
    val context: List<String>,
    val perspectives: List<DeepDivePerspective>,
    val impacts: List<DeepDiveImpact>,
)

data class DeepDivePerspective(val school: String, val view: String)

data class DeepDiveImpact(val area: String, val shortTerm: String, val longTerm: String)
