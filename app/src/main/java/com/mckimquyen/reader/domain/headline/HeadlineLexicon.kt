package com.mckimquyen.reader.domain.headline

internal object HeadlineLexicon {

    // Function words for conservative language identification
    val VI_MARKERS = setOf(
        "và", "của", "trong", "được", "người", "những", "cho", "với", "không", "này",
        "các", "một", "có", "đã", "sẽ", "là", "tại", "về", "khi", "như", "đang", "từ"
    )

    val EN_MARKERS = setOf(
        "the", "and", "in", "to", "of", "a", "is", "for", "on", "that", "by", "with",
        "as", "at", "from", "after", "its", "who", "will", "are", "be", "has", "over",
        "you", "your", "what", "how", "why", "this", "these", "next", "out", "now", "it",
        "shocking", "secret", "truth", "unbelievable", "council", "approves", "route", "during"
    )

    val ES_MARKERS = setOf(
        "el", "la", "de", "que", "y", "en", "un", "una", "los", "las", "por", "para",
        "con", "no", "una", "del", "al", "se", "es", "su", "lo", "como", "más", "nadie", "esperaba", "impactante", "verdad"
    )

    val FR_MARKERS = setOf(
        "le", "la", "les", "de", "des", "du", "et", "en", "un", "une", "pour", "dans",
        "qui", "sur", "par", "avec", "est", "au", "aux", "ce", "cette", "ces", "incroyable", "personne", "attendait", "vérité", "verite"
    )

    val DE_MARKERS = setOf(
        "der", "die", "das", "und", "in", "den", "von", "zu", "mit", "ist", "im", "für",
        "auf", "dem", "nicht", "ein", "eine", "einer", "nach", "bei", "als", "um"
    )

    // Clickbait trigger phrases
    val EN_BAIT_PHRASES = listOf(
        "won't believe", "wont believe", "you won't believe", "you wont believe",
        "the truth about", "shocking", "unbelievable", "mind blowing", "mind-blowing",
        "what happens next", "will blow your mind", "blow your mind", "blows your mind",
        "this is why", "here's why", "heres why", "the reason why", "see why",
        "everyone is talking about", "goes viral", "breaks the internet", "this secret",
        "will shock you", "don't want you to know", "dont want you to know"
    )

    val VI_BAIT_PHRASES = listOf(
        "sự thật chấn động", "chấn động", "không thể tin", "không tin nổi", "bạn sẽ không tin",
        "điều gì sẽ xảy ra", "điều gì vừa xảy ra", "hé lộ sự thật", "sự thật đằng sau",
        "ai cũng sốc", "khiến ai cũng sốc", "choáng váng", "dậy sóng mạng", "gây bão",
        "nguyên nhân thực sự", "lý do vì sao", "bí mật đằng sau", "không muốn bạn biết",
        "nhìn là sốc", "ngỡ ngàng", "bật ngửa"
    )

    val ES_BAIT_PHRASES = listOf(
        "no vas a creer", "no te vas a creer", "la verdad sobre", "impactante",
        "lo que pasa después", "lo que pasó después", "por esta razón", "nadie esperaba",
        "te sorprenderá", "no quieren que sepas", "se vuelve viral"
    )

    val FR_BAIT_PHRASES = listOf(
        "vous ne croirez pas", "la vérité sur", "incroyable", "ce qui s'est passé",
        "ce qui arrive après", "voici pourquoi", "personne n'attendait", "va vous choquer",
        "ne veulent pas que vous sachiez", "devient viral"
    )

    val DE_BAIT_PHRASES = listOf(
        "sie werden nicht glauben", "du wirst nicht glauben", "die wahrheit über",
        "unglaublich", "was als nächstes geschieht", "was dann geschah", "hier ist warum",
        "niemand erwartet hat", "wird sie schockieren", "nicht wissen wollen", "geht viral"
    )

    // Curiosity and question patterns
    val EN_CURIOSITY_PATTERNS = listOf(
        "what the", "how this", "why this", "this one", "this trick", "these simple",
        "this simple", "is this the end", "what really happened", "why you should"
    )

    val VI_CURIOSITY_PATTERNS = listOf(
        "điều này", "mẹo này", "cách này", "liệu có phải", "thực hư chuyện", "tại sao bạn nên"
    )

    val ES_CURIOSITY_PATTERNS = listOf("por qué esto", "este truco", "lo que realmente pasó")
    val FR_CURIOSITY_PATTERNS = listOf("pourquoi ceci", "cette astuce", "ce qui s'est vraiment passé")
    val DE_CURIOSITY_PATTERNS = listOf("warum das", "dieser trick", "was wirklich geschah")

    // Intensifiers
    val EN_INTENSIFIERS = setOf(
        "shocking", "unbelievable", "insane", "epic", "bizarre", "terrifying", "secret",
        "stunning", "spectacular", "horrifying", "miraculous", "breathtaking", "unreal"
    )

    val VI_INTENSIFIERS = setOf(
        "chấn động", "kinh hoàng", "khủng khiếp", "không tưởng", "kinh ngạc", "bàng hoàng",
        "sốc", "bí mật", "kỳ diệu", "ngỡ ngàng", "thót tim", "kinh sợ"
    )

    val ES_INTENSIFIERS = setOf("impactante", "increíble", "insólito", "secreto", "asombroso", "estupendo")
    val FR_INTENSIFIERS = setOf("incroyable", "choquant", "secret", "fou", "sidérant", "renversant")
    val DE_INTENSIFIERS = setOf("unglaublich", "schockierend", "geheim", "wahnsinnig", "atemberaubend")

    // Sentiment tokens
    val EN_POSITIVE = setOf(
        "celebrate", "celebrates", "celebrating", "win", "wins", "winner", "victory",
        "breakthrough", "triumph", "praised", "award", "heroes", "rescued", "success",
        "joy", "hope", "inspire", "inspiring", "milestone", "boost", "record", "saves"
    )

    val EN_NEGATIVE = setOf(
        "deadly", "destroys", "destroyed", "crisis", "disaster", "collapse", "mourn",
        "mourns", "tragedy", "fail", "failed", "scandal", "fatal", "outrage", "horror",
        "grief", "fears", "catastrophe", "crash", "losses", "slump", "victim", "victims"
    )

    val VI_POSITIVE = setOf(
        "thành công", "chiến thắng", "vinh danh", "kỷ lục", "khen ngợi", "vui mừng",
        "phấn khởi", "tự hào", "đột phá", "cứu sống", "hy vọng", "khởi sắc", "thăng hoa"
    )

    val VI_NEGATIVE = setOf(
        "thảm kịch", "tang thương", "chết", "tử vong", "tai nạn", "phẫn nộ", "khủng hoảng",
        "suy thoái", "sụp đổ", "đau xót", "bê bối", "đổ vỡ", "thiệt hại", "nạn nhân"
    )

    val ES_POSITIVE = setOf("triunfo", "victoria", "celebra", "éxito", "héroe", "esperanza", "rescate")
    val ES_NEGATIVE = setOf("tragedia", "crisis", "desastre", "colapso", "escándalo", "muerte", "luto")

    val FR_POSITIVE = setOf("victoire", "succès", "célèbre", "triomphe", "héros", "espoir", "sauvé")
    val FR_NEGATIVE = setOf("tragédie", "crise", "désastre", "effondrement", "scandale", "mort", "deuil")

    val DE_POSITIVE = setOf("sieg", "erfolg", "feiert", "triumph", "held", "hoffnung", "gerettet")
    val DE_NEGATIVE = setOf("tragödie", "krise", "katastrophe", "kollaps", "skandal", "tod", "trauer")
}
