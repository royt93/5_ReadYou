# Chuỗi dịch máy nghi ngờ, 22 locale (2026-11-24)

Nguồn: tôi dịch bằng mô hình ngôn ngữ, mỗi locale một lượt, tự đánh dấu chuỗi không chắc. Chưa người bản ngữ nào duyệt. Danh sách này chỉ là chỗ tôi tự biết dễ sai, không phải toàn bộ chỗ có thể sai.

Cách kiểm: mở `app/src/main/res/values-<locale>/strings.xml`, tìm key. Sửa thì chạy `StringResourcesHealthTest` (giữ placeholder, không rỗng).

## az (25 chuỗi)
- `tos_tips`: Source says RSS Bear Hub (differs from RSS Cat Hub); kept verbatim.
- `zen_sound_binaural`: Gamma transliterated as Qamma; technical term.
- `zen_sound_bowl`: Tibetan singing bowl rendered literally; no standard Azerbaijani term.
- `source_dna_action`: DNA is DNT in Azerbaijani; may prefer keeping DNA.
- `source_dna_title`: DNA is DNT in Azerbaijani; may prefer keeping DNA.
- `bounty_sheet_title`: Entity rendered as varliq; ambiguous NLP term.
- `dossier_names_note`: Named-entity recognition phrased descriptively.
- `widget_article_pin_requested`: Widget rendered as vidcet; launcher kept English.
- `feed_zero_triage_title`: Idiomatic title; literal rendering.
- `triage_inbox_zero_title`: Inbox Zero idiom.
- `zen_calibration_limitation`: Hex/WPM abbreviations replaced with soz/deq; check wording.
- `zen_wpm_label`: WPM rendered as soz/deq, not abbreviation.
- `local_sync_pairing_limits`: Forward secrecy phrased as ireli mexfilik; technical term.
- `local_sync_monotonic_warning`: Awkward English source; meaning interpreted.
- `local_sync_wait_reply`: Ambiguous English button label.
- `echo_cue_exclamation`: Source label ambiguous.
- `echo_hype_warning`: Hype wording translated as shishirdilmish.
- `echo_flagged_passages_title`: Passages rendered as hisseler.
- `echo_dual_share_text`: Hashtag kept unchanged.
- `brain_rpg_category_philosophy`: Ideas typed with dotted capital I, check spelling Ideyalar vs Ideyalar.
- `oracle_bet_settled_refunded`: Natural phrasing uncertain.
- `mindmap_node_details`: Copy Insight literal.
- `notebook_empty_subtitle`: Second Brain term translated as Ikinci Beyin.
- `zen_daily_edition`: Edition rendered as buraxilish.
- `headline_signals_desc`: Long technical text; lexical rendered as leksik.

## bg (16 chuỗi)
- `zen_sound_bowl`: Singing bowl term: 'пееща купа' may differ from common usage
- `tonal_elevation`: Material term; 'тонално повдигане' not an established Bulgarian UI term
- `fill_selected_icon`: Ambiguous English ('current filter icon')
- `zen_daily_edition_enabled`: 'Batch Delivery Mode' rendered as 'групова доставка', unclear wording
- `triage_inbox_zero_title`: 'Inbox Zero' idiom rendered as 'празна кутия'
- `feed_zero_triage_title`: 'Feed Zero Triage' idiom; translated as 'Сортиране до нула'
- `second_brain_title`: Feature name translated as 'Втори мозък'
- `brain_rpg_wrapped_title`: 'Brain Wrapped' idiom rendered as 'мозъчен преглед'
- `local_sync_pairing_limits`: 'forward secret' left as English term 'forward secrecy'
- `local_sync_monotonic_warning`: Technical concept; wording approximate
- `source_dna_title`: 'Source DNA' translated as 'ДНК на източника'
- `echo_details_title`: 'Body Language Signals' (wording of text) rendered loosely
- `bounty_sheet_title`: 'Entity' translated as 'субект'; may read oddly
- `dossier_names_note`: 'named-entity recognition' translated loosely
- `deep_dive_menu`: 'Deep Dive' translated as 'задълбочен анализ'; consistency with commute_unlock_deepdive
- `brain_rpg_xp_retained`: Short mixed phrase with %%; word order approximate

## ca (36 chuỗi)
- `zen_wpm_label`: WPM rendered as ppm (paraules per minut); standard Catalan abbreviation but unusual in UI
- `zen_calibration_suggested_wpm`: WPM rendered as ppm
- `zen_calibration_round_header`: WPM rendered as ppm
- `zen_sound_binaural`: Technical term 'Binaural 40Hz Gamma' adapted
- `zen_daily_edition_enabled`: 'Batch Delivery Mode' rendered as 'lliurament per lots'
- `highlights_fallback_badge`: 'Offline Smart' short badge; 'Intel·ligent sense connexió' is long
- `deep_read_offline_badge`: Same 'Offline Smart' badge
- `mindmap_offline_badge`: 'Smart Graph' rendered as 'Graf intel·ligent'
- `source_dna_title`: 'Source DNA' kept metaphor as 'ADN de la font'
- `echo_dual_share_text`: Hashtag #Debate left in English; could be #Debat
- `local_sync_role_hint`: 'Receive' tab label assumed to be 'Rep'; must match the actual tab string
- `local_sync_no_peer_found`: References 'Send' tab as 'Envia'; must match actual label
- `local_sync_reply_instruction`: References 'Receive' as 'Rep'; must match actual label
- `local_sync_pairing_limits`: 'forward secret' rendered as 'secret de reenviament'; non-standard
- `local_sync_monotonic_warning`: Phrase 'only moves forward' paraphrased
- `feed_zero_triage_title`: 'Feed Zero Triage' idiom
- `triage_inbox_zero_title`: 'Inbox Zero' rendered as 'Safata a zero'
- `feed_autopilot_reason_opened`: 'engagement' rendered as 'participació'
- `bounty_sheet_title`: 'Entity Investigation Agent' wording
- `echo_disclaimer`: 'lexical evidence' wording; quotes converted to escaped straight quotes
- `echo_hype_warning`: 'Hype' rendered as 'exagerat/promocional'
- `echo_no_passages`: 'Hype threshold' rendered as 'llindar d'exageració'
- `archive_query_start_date`: YYYY-MM-DD changed to AAAA-MM-DD; format validation is likely YYYY so users must type that pattern; check bad_date string consistency
- `archive_query_end_date`: Same AAAA-MM-DD vs YYYY-MM-DD issue
- `archive_query_problem_bad_date`: Same AAAA-MM-DD vs YYYY-MM-DD issue
- `archive_query_problem_clarify_dates`: First-person 'No he pogut llegir' tone
- `oracle_bet_pick_side`: 'Your pick' short label
- `oracle_bet_settled_refunded`: Wording 'T'hem tornat' (we returned) differs from passive source
- `second_brain_title`: Feature name translated as 'Segon Cervell'; may be kept English
- `quote_card_template_dark`: 'Dark Studio' template name translated
- `brain_rpg_wrapped_title`: 'Brain Wrapped' (Spotify-style) idiom translated as 'Resum setmanal del cervell'
- `brain_wrapped_share_title`: Same 'Wrapped' idiom
- `brain_wrapped_header`: Same 'Wrapped' idiom
- `brain_rpg_xp_retained`: 'Base' kept as is; 'XP' kept
- `headline_signals_desc`: Long technical text; 'Spanish' rendered as 'castellà'
- `spanish_sources`: 'castellà' vs 'espanyol'; both valid

## cs-rCZ (35 chuỗi)
- `zen_wpm_label`: WPM translated as 'slov/min'; check it fits UI width
- `zen_sound_binaural`: 'Binaural 40Hz Gamma' rendered as 'Binaurální gama 40 Hz'; terminology
- `story_clustering_title`: 'Deduplication' rendered as 'odstranění duplicit'; wording
- `source_dna_action`: 'Source DNA' feature name translated as 'DNA zdroje'; may want to keep English
- `bounty_sheet_title`: 'Entity Investigation Agent' - 'Agent pro vyšetřování entit' is a literal rendering
- `echo_details_title`: 'Body Language Signals' is ambiguous (article body text, not body language); rendered as 'Signály jazyka textu'
- `echo_hype_warning`: 'Hype' term; rendered as 'přehnané formulace'
- `echo_no_passages`: 'Hype threshold' rendered as 'práh přehnanosti'
- `dossier_create_action`: 'dossier' rendered as 'dosje'; Czech spelling variants (dosié/dosje)
- `dossier_names_note`: 'named-entity recognition' rendered as 'rozpoznávání pojmenovaných entit'
- `local_sync_pairing_limits`: 'forward secret' rendered as 'dopředná bezpečnost'; technical term
- `local_sync_monotonic_warning`: Idea of read state moving only forward; phrasing may be awkward
- `local_sync_pairing_secret`: 'pairing secret' rendered as 'párovací tajemství'; possibly 'párovací klíč' preferred
- `feed_zero_triage_title`: 'Feed Zero Triage' idiom rendered as 'Třídění kanálů do nuly'
- `triage_inbox_zero_title`: 'Inbox Zero' idiom rendered as 'Schránka je prázdná'
- `deep_read_welcome_message`: Chatbot voice; masculine past tense 'Analyzoval jsem' may need to be neutral
- `brain_rpg_wrapped_title`: 'Brain Wrapped' idiom (Spotify Wrapped) rendered as 'přehled mozku'
- `brain_rpg_skill_tree_decay_warning`: Decay metaphor ('faded') wording
- `brain_rpg_decay_notification_channel_desc`: 'category decay' rendered as 'úpadek kategorií'
- `oracle_bet_pick_side`: 'Your pick' in betting context rendered as 'Váš tip'
- `oracle_bet_place_summary`: Czech case after 'na' with %2$s placeholder may be ungrammatical depending on value; used 'na: %2$s'
- `oracle_bet_open`: Same placeholder-case issue as oracle_bet_place_summary
- `oracle_bet_awaiting_hint`: 'Unclear' button name must match the button label in the UI
- `oracle_bet_out_of_points`: 'UTC' day reset phrasing
- `commute_ready_to_play_body`: 'Play' button name must match the label used in UI
- `commute_listen_time_title`: Wording of 'listen time'
- `widget_article_pin_requested`: 'launcher' kept as loanword
- `zen_daily_edition_enabled`: 'Batch Delivery Mode' rendered as 'Režim dávkového doručování'
- `quote_card_template_dark`: 'Dark Studio' template name translated as 'Tmavé studio'
- `second_brain_title`: Kept 'Second Brain' as feature name untranslated
- `echo_dual_share_text`: Hashtag left unchanged
- `vip_remaining_short`: Czech plural forms for days/hours not handled (dní/hodin used for all)
- `commute_host_alex`: 'Anchor' rendered as 'hlavní moderátor'
- `mindmap_node_details`: 'Copy Insight' rendered as 'Kopírovat poznatek'
- `zen_speed_reading`: 'RSVP' technique name kept; 'rychločtení' term

## da (23 chuỗi)
- `brain_rpg_streak`: 'streak' rendered as 'serie'; check consistency/naturalness
- `brain_rpg_streak_shield`: 'Serieskjold' coined term
- `zen_speed_reading`: RSVP kept as acronym, 'hurtiglæsning' term
- `zen_sound_binaural`: 'Binaural 40 Hz gamma' technical term
- `highlights_fallback_badge`: 'Offline smart' short badge, ambiguous English
- `mindmap_offline_badge`: 'Smart graf' ambiguous English
- `source_dna_disclaimer`: 'Heuristik' wording
- `headline_signals_desc`: 'slagside' for bias, 'leksikalsk' technical
- `headline_signals_tone_desc_positive`: 'Leksikalsk overskriftstone' technical
- `echo_disclaimer`: 'Uklar' vs neutral nuance; 'slagside'
- `echo_cue_exclamation`: 'Punctuation or uppercase emphasis' phrasing
- `echo_hype_warning`: 'hype' tone
- `dossier_names_note`: named-entity recognition phrasing
- `local_sync_pairing_limits`: 'forward secrecy' technical term left in English
- `local_sync_monotonic_warning`: hard to render clearly
- `tips_top_bar_tonal_elevation`: 'Tonal height' odd source; translated as 'Tonehøjde'
- `triage_inbox_zero_title`: 'Indbakke tom' idiom
- `feed_zero_triage_title`: 'Feed Zero' product-like name kept
- `oracle_bet_settled_refunded`: tone of virtual betting wording
- `oracle_bet_awaiting_hint`: 'Unclear' as button name translated 'Uklart'; must match button label
- `notebook_empty_subtitle`: 'Second Brain' kept as feature name
- `bionic_reading_desc`: 'Bold the first letters' phrasing
- `quote_card_template_dark`: 'Dark Studio' template name translated

## eu-rES (25 chuỗi)
- `zen_sound_bowl`: Tibeteko ontzi kantaria: term for singing bowl uncertain
- `brain_rpg_xp_retained`: Word order around placeholders with XP/percent
- `zen_calibration_round_header`: Basque ordinal '%1$d. txanda 3tik' with placeholder
- `oracle_bet_place_summary`: 'Bet X points on Y' reworded with 'honen alde: Y'; case after placeholder unknown
- `oracle_bet_open`: Same: 'honen alde: %2$s' phrasing
- `oracle_bet_awaiting_hint`: 'Unclear' reference as 'Argi gabea' must match button label
- `echo_disclaimer`: Long nuanced sentence; 'Unclear' quoted term must match UI label
- `headline_signals_desc`: Long technical text; 'clickbait', 'hype' kept in English
- `archive_query_start_date`: Date format YYYY-MM-DD localized as UUUU-HH-EE; app parser expects YYYY-MM-DD
- `archive_query_end_date`: Same date-format localization risk
- `archive_query_problem_bad_date`: Same date-format localization risk
- `feed_zero_triage_title`: 'Feed Zero' idiom
- `triage_inbox_zero_title`: 'Inbox Zero' idiom -> 'Sarrera-ontzia hutsik'
- `notebook_empty_subtitle`: 'Second Brain' translated as 'Bigarren Garuna'
- `second_brain_title`: 'Second Brain' concept term
- `brain_rpg_wrapped_title`: 'Wrapped' idiom
- `local_sync_pairing_limits`: 'forward secret' cryptographic term
- `local_sync_monotonic_warning`: Starred rendered as 'izardun'; technical phrasing
- `quote_card_template_dark`: 'Dark Studio' template name translated
- `highlights_fallback_badge`: 'Offline Smart' badge wording
- `mindmap_offline_badge`: 'Smart Graph' badge wording
- `zen_sound_binaural`: Kept 'Binaural 40Hz Gamma' mostly English
- `zen_ambient_audio`: Product-ish name 'Zen Focus Audio' translated
- `brain_rpg_title`: Kept as feature name
- `oracle_bet_out_of_points`: Number format 2.000 / 100 points; Basque thousand sep

## fa (22 chuỗi)
- `brain_rpg_wrapped_title`: 'Brain Wrapped' idiom (Spotify Wrapped style); rendered as 'مرور هفتگی مغز'
- `brain_rpg_streak`: 'Streak' rendered as 'رکورد' (record); could be 'زنجیره'
- `zen_sound_binaural`: 'Binaural 40Hz Gamma' technical; rendered 'گامای ۴۰ هرتز دوگوشی'
- `echo_details_title`: 'Body Language Signals' refers to body text wording, rendered 'نشانه‌های زبان متن'
- `echo_hype_warning`: 'Hype' term; used 'هیاهو'/'اغراق‌آمیز'
- `echo_no_passages`: 'Hype threshold' rendered 'آستانهٔ هیاهو'
- `headline_signals_title`: 'Clickbait' rendered 'کلیک‌طعمه'; not a settled Persian term
- `local_sync_pairing_limits`: 'not forward secret' crypto term rendered 'محرمانگی پیشرو'
- `local_sync_monotonic_warning`: Awkward English about one-way state; meaning inferred
- `bounty_sheet_title`: 'Entity Investigation Agent' technical; 'entity' as 'موجودیت'
- `dossier_names_note`: 'named-entity recognition' term paraphrased
- `feed_autopilot_title`: 'Autopilot' rendered 'خلبان خودکار'
- `feed_autopilot_action_snooze`: 'Snooze' idiom paraphrased as 'به تعویق برای ۳۰ روز'
- `triage_summary_skipped`: Value says 'Marked as read'; wording of 'علامت‌خورده' is stiff
- `feed_zero_triage_title`: 'Feed Zero Triage' idiom
- `triage_swipe_up_read`: Swipe-up label rendered 'همین حالا بخوان'
- `zen_daily_edition_enabled`: 'Batch Delivery Mode' rendered 'حالت تحویل دسته‌ای'
- `source_dna_action`: 'Source DNA' feature name translated partly ('DNA منبع')
- `oracle_bet_place_summary`: Placeholder %2$s is a side/outcome name; grammar 'روی' may not fit all values
- `archive_query_title`: 'Ask My Archive' first-person tone
- `deep_dive_section_critical`: 'Opposing views' for a critical section
- `story_cluster_badge`: %d placeholders with plural; Persian number handling fine but phrasing approximate

## hi-rIN (22 chuỗi)
- `zen_sound_pink_noise`: 'Soft Focus Noise' transliterated; no standard Hindi term
- `zen_sound_binaural`: technical audio term transliterated
- `feed_zero_triage_title`: 'Triage' transliterated as ट्रायाज; unusual in Hindi UI
- `triage_inbox_zero_title`: 'Inbox Zero' transliterated
- `bounty_sheet_title`: 'Entity' rendered as एंटिटी; no common Hindi term
- `echo_cue_exclamation`: 'Punctuation or uppercase emphasis' - uppercase concept awkward in Devanagari
- `echo_no_passages`: 'Hype threshold' kept as हाइप
- `dossier_names_note`: 'capitalized phrases' / 'named-entity recognition' hard to render in Hindi
- `local_sync_monotonic_warning`: 'Monotonic' concept paraphrased
- `local_sync_pairing_limits`: 'forward secret' rendered literally as फ़ॉरवर्ड सीक्रेट
- `local_sync_error_secret`: 'lowercase hex characters' literal rendering
- `local_sync_role_hint`: Send/Receive tab labels must match other strings (प्राप्त करें / भेजें)
- `local_sync_reply_instruction`: 'Receive' button label reference must match UI label
- `archive_query_dates_optional`: 'override' phrase rendered idiomatically
- `oracle_bet_balance`: 'points' rendered as अंक; betting terminology (दाँव)
- `oracle_bet_awaiting_hint`: 'Unclear' refers to a button label; must match that label (अस्पष्ट)
- `deep_dive_section_impact`: 'Impact matrix' literal rendering
- `streak_rescue_message`: Hindi plural of days with placeholders fine, but 'missed reading' phrasing is free
- `story_clustering_title`: 'Deduplication' paraphrased as डुप्लिकेट हटाना
- `mindmap_node_details`: 'Copy Insight' - label for node detail action, ambiguous
- `quote_card_template_dark`: 'Dark Studio' template name transliterated
- `bionic_reading_desc`: 'skim' rendered as सरसरी नज़र

## hu (20 chuỗi)
- `source_dna_title`: 'Source DNA' brand-like feature name rendered as 'Forrás eredete' (source origin)
- `echo_details_title`: 'Body Language Signals' means article body wording; 'Szövegtest-jelzések' is awkward
- `echo_hype_warning`: 'Hype' rendered as exaggerated wording; echo_no_passages uses 'felhajtásküszöb' for 'Hype threshold'
- `echo_no_passages`: 'Hype threshold' translated as 'felhajtásküszöb'
- `bounty_sheet_title`: 'Entity Investigation Agent' - 'entitás' is a stiff but standard term
- `dossier_names_note`: 'named-entity recognition' translated as 'névelemfelismerés'
- `local_sync_pairing_limits`: 'forward secret' translated as 'előre titkosság' (technical term)
- `brain_rpg_streak_shield`: 'Streak Shield' translated as 'Sorozatvédő pajzs'
- `zen_sound_binaural`: 'Binaural 40Hz Gamma' technical audio name
- `zen_speed_reading`: RSVP kept as acronym; 'Gyorsolvasás'
- `feed_zero_triage_title`: 'Feed Zero Triage' translated as 'Hírcsatorna-ürítés', loses 'triage' nuance
- `triage_inbox_zero_title`: 'Inbox Zero' translated as 'Üres a postaláda'
- `vip_entry_first_install`: 'New install gift' rephrased
- `widget_article_pin_unsupported`: 'launcher' translated as 'indító'; Hungarian UIs often keep 'launcher'
- `oracle_bet_place_summary`: Hungarian case suffix after %2$s placeholder not possible; phrased as 'feltétele erre:'
- `oracle_bet_awaiting_hint`: Refers to an 'Unclear' button label; 'Nem egyértelmű' must match the button in the app
- `echo_disclaimer`: Refers to 'Unclear' label; must match headline_signals tone label
- `feed_autopilot_reason_zero`: Ordinal phrase '(a)(z) %1$d közül' reads slightly stiff
- `streak_rescue_message`: Plural/number agreement is fine but sentence is long
- `zen_calibration_round_header`: Ordinal '%1$d. kör a 3-ból' with placeholders

## iw (20 chuỗi)
- `tonal_elevation`: Material term 'tonal elevation'; no standard Hebrew term, rendered 'גובה גוון'
- `tips_top_bar_tonal_elevation`: 'Tonal height' rendered as 'גובה גוון'
- `bionic_reading`: Feature name; 'קריאה ביונית' is a literal rendering
- `feed_zero_triage_title`: Idiom 'Feed Zero Triage'; rendered loosely
- `feed_autopilot_action_snooze`: 'Snooze' for feeds is ambiguous; rendered as 'הנמכה' (mute/lower), may need a better verb
- `source_dna_title`: 'Source DNA' metaphor kept literal
- `echo_details_title`: 'Body Language Signals' means wording signals in article body; metaphor rendered loosely
- `zen_sound_binaural`: Technical audio term 'Binaural 40Hz Gamma'
- `zen_sound_bowl`: 'Tibetan Singing Bowl' rendered 'קערת שירה טיבטית'
- `brain_rpg_xp_retained`: Ambiguous '%% retained' structure, kept short
- `bounty_sheet_title`: 'Entity Investigation Agent' - 'entity' (ישות) is a technical term
- `dossier_names_note`: 'named-entity recognition' rendered 'זיהוי ישויות מובנה', technical term
- `local_sync_http_warning`: Technical networking terms
- `local_sync_pairing_limits`: 'forward secret' rendered 'סודיות קדימה' (forward secrecy)
- `local_sync_monotonic_warning`: Awkward English concept; paraphrased
- `local_sync_role_hint`: Send/Receive mode names must match other labels in UI
- `second_brain_title`: 'Second Brain' concept rendered 'מוח שני'
- `commute_unlock_deepdive`: 'Deep Dive' rendered 'צלילה לעומק'
- `streak_rescue_message`: Plural/number agreement for %1$d days not handled in Hebrew
- `oracle_bet_awaiting_hint`: 'Unclear' option label must match the actual button label elsewhere

## kn (23 chuỗi)
- `tonal_elevation`: Material term; kept as transliterated ಟೋನಲ್ ಎತ್ತರ
- `bionic_reading`: Brand-like feature name, transliterated
- `brain_rpg_streak`: Streak transliterated as ಸ್ಟ್ರೀಕ್, no common Kannada UI term
- `brain_rpg_xp_retained`: Terse gaming wording, meaning inferred
- `zen_sound_binaural`: Technical audio term, transliterated
- `zen_sound_pink_noise`: Rendered as soft focus noise, source is a nickname
- `zen_calibration_title`: Calibration rendered as ಮಾಪನಾಂಕ, formal term
- `echo_cue_exclamation`: Ambiguous English, translated by meaning
- `echo_hype_warning`: Hype/promotional wording nuance
- `echo_disclaimer`: Long, nuanced disclaimer with quoted Unclear
- `headline_signals_desc`: Long technical text, lexical terms
- `dossier_names_note`: Named-entity recognition phrased descriptively
- `local_sync_pairing_limits`: forward secret rendered as ಫಾರ್ವರ್ಡ್ ಸೀಕ್ರೆಸಿ, no standard Kannada
- `local_sync_monotonic_warning`: Technical state-merge wording
- `local_sync_wait_reply`: Terse English, meaning inferred
- `feed_zero_triage_title`: Feed Zero Triage is a coined feature name
- `triage_inbox_zero_title`: Inbox Zero idiom transliterated
- `oracle_bet_pick_stake`: Betting terminology ಪಣ
- `oracle_bet_place_summary`: Reordered so %2$s is the pick; verify grammar
- `bounty_sheet_title`: Entity rendered as ಘಟಕ; may be unnatural
- `quote_card_template_dark`: Dark Studio template name translated partly
- `archive_query_shift_title`: Idiomatic English
- `second_brain_title`: Kept in English as feature name

## lzh (15 chuỗi)
- `bionic_reading`: Bionic Reading is a feature name; rendered as 仿生閱讀
- `zen_sound_binaural`: technical term, 伽瑪波 transliteration
- `zen_speed_reading`: RSVP kept; 速讀 reasonable
- `echo_hype_warning`: hype rendered 宣傳或誇大, may need tuning
- `echo_cue_exclamation`: wording of punctuation/uppercase emphasis
- `source_dna_disclaimer`: heuristic -> 啟發式; technical register in classical Chinese
- `dossier_names_note`: named-entity recognition rendered literally
- `local_sync_pairing_limits`: forward secret -> 前向保密, modern technical term
- `local_sync_monotonic_warning`: monotonic idiom paraphrased
- `local_sync_http_warning`: technical networking terms
- `oracle_bet_pick_stake`: stake/bet gambling terms paraphrased
- `quote_card_template_dark`: Dark Studio is a template name; translated as 暗室
- `preview_article_title`: Lord of the Mysteries rendered as 詭秘之主 (known Chinese title)
- `feed_zero_triage_title`: Feed Zero Triage concept coined: 清零分揀
- `second_brain_title`: Second Brain concept: 第二大腦

## ml (20 chuỗi)
- `tonal_elevation`: Material term; transliterated
- `bionic_reading`: Brand-like feature name; transliterated
- `brain_rpg_streak`: 'streak' kept as transliteration സ്ട്രീക്ക്
- `zen_sound_pink_noise`: Audio term, transliterated
- `zen_sound_binaural`: Technical audio term
- `source_dna_disclaimer`: 'heuristic' and 'attribution' technical terms
- `echo_cue_exclamation`: Ambiguous English phrase
- `echo_details_title`: 'Body Language Signals' likely means article body wording; translated literally
- `echo_hype_warning`: 'hype' wording
- `local_sync_pairing_limits`: 'forward secret' is a crypto term
- `local_sync_monotonic_warning`: Abstract sync semantics
- `local_sync_role_hint`: Refers to Send/Receive button labels
- `oracle_bet_place`: Gambling-flavoured term for 'bet' (പന്തയം)
- `triage_inbox_zero_title`: 'Inbox Zero' idiom
- `feed_zero_triage_title`: 'Triage' rare term, transliterated
- `feed_autopilot_summary_noisy`: 'noisy feeds' idiom (ശബ്ദമയമായ)
- `dossier_names_note`: 'named-entity recognition' technical
- `archive_query_shift_cues`: Quote placeholders
- `quote_card_template_dark`: Template name
- `widget_article_pin_unsupported`: 'pinning' widget term

## my (25 chuỗi)
- `tonal_elevation`: Material 'tonal elevation' has no standard Burmese term
- `bionic_reading_desc`: Bionic Reading concept paraphrased
- `zen_sound_binaural`: technical name kept mostly in English
- `zen_sound_bowl`: Tibetan singing bowl rendered descriptively
- `brain_rpg_streak_shield`: 'Shield' rendered as ဒိုင်း; game term
- `brain_rpg_xp_retained`: 'retained XP' phrasing
- `echo_hype_warning`: 'hype/exaggerated' nuance
- `echo_disclaimer`: long technical disclaimer; 'lexical evidence' nuance
- `echo_no_passages`: 'Hype threshold' kept as Hype
- `headline_signals_desc`: long technical text, clickbait/tone terms
- `source_dna_disclaimer`: 'heuristic' rendered as ခန့်မှန်းချက်
- `dossier_names_note`: 'named-entity recognition' paraphrased
- `local_sync_pairing_limits`: 'forward secret' kept as English 'forward secrecy'
- `local_sync_monotonic_warning`: monotonic merge semantics paraphrased
- `oracle_bet_place`: betting/gambling terms; ထိုးရန်/လောင်းကြေး choice
- `oracle_bet_out_of_points`: UTC daily grant wording
- `quote_card_template_dark`: left as English template name
- `feed_zero_triage_title`: 'Feed Zero Triage' concept paraphrased
- `triage_inbox_zero_title`: 'Inbox Zero' paraphrased as ဝင်စာပုံးသုည
- `feed_autopilot_title`: 'Autopilot' transliterated
- `podcast_studio_waveform_playing`: 'Visualizer' rendered as အသံလှိုင်းပြသမှု
- `galaxy_canvas_desc`: accessibility description wording
- `watchdog_add_hint`: ticker translated as စတော့ကုဒ်
- `tos_tips`: source says 'RSS Bear Hub's' (differs from product name RSS Cat Hub); kept as in source
- `archive_query_limits`: long constraint list

## nb-rNO (12 chuỗi)
- `brain_rpg_streak_shield`: 'Streak' rendered as 'rekke'; no settled Norwegian term
- `zen_calibration_low_confidence_note`: natural phrasing uncertain
- `local_sync_pairing_limits`: 'forward secrecy' left in English; no common Norwegian term
- `local_sync_monotonic_warning`: abstract wording about one-way read state
- `echo_disclaimer`: 'lexical evidence' rendered as 'leksikalsk grunnlag'
- `headline_signals_tone_desc_positive`: 'Lexical headline tone' rendered as 'leksikalsk tone'
- `oracle_bet_place`: 'bet' as 'spill'/'sats' mixed; gambling terminology choice
- `zen_sound_binaural`: technical audio name
- `bounty_sheet_title`: 'Entity Investigation Agent' rendered as 'Enhetsetterforsker'
- `galaxy_hint`: UI gesture wording
- `feed_zero_triage_title`: feature name kept partly English
- `dossier_names_note`: 'named-entity recognition' translated literally

## nl (31 chuỗi)
- `initial_open_app`: Ambiguous English ('App when link is clicked'); translated as app used on link tap
- `tips_top_bar_tonal_elevation`: Material 'tonal elevation' is a rare term; rendered as 'tonale hoogte'
- `brain_rpg_streak_shield`: Idiom 'streak shield'; rendered as 'Reeksbescherming'
- `zen_sound_binaural`: Technical audio term 'Binaural 40Hz Gamma'
- `zen_daily_edition_enabled`: 'Batch Delivery Mode' is ambiguous
- `highlights_fallback_badge`: 'Offline Smart' badge is terse/ambiguous
- `mindmap_offline_badge`: 'Smart Graph' badge; rendered as 'Slimme graaf'
- `deep_read_offline_badge`: 'Offline Smart' badge is terse/ambiguous
- `source_dna_action`: Feature name 'Source DNA'; translated as 'Bron-DNA'
- `watchdog_title`: Feature name 'Keyword Watchdog'; rendered as 'Trefwoordwachter'
- `feed_zero_triage_title`: 'Feed Zero Triage' feature name; kept 'Feed Zero' with 'triage'
- `bounty_sheet_title`: 'Entity Investigation Agent'; 'entiteit' jargon
- `echo_details_title`: 'Body Language Signals' is odd; rendered as signals in wording
- `echo_cue_exclamation`: 'Punctuation or uppercase emphasis' phrasing
- `echo_contrast_limitation`: 'contrarian headline wording' is idiomatic
- `dossier_sheet_title`: 'Local intelligence dossier' - 'inlichtingendossier' sounds like spy intelligence
- `dossier_names_note`: 'named-entity recognition' technical term rendered as 'herkenning van benoemde entiteiten'
- `local_sync_monotonic_warning`: Ambiguous 'starred' (ster) and 'monotonic' concept
- `local_sync_pairing_limits`: 'forward secret' crypto term kept as 'forward secrecy'
- `local_sync_secret_warning`: 'pairing secret' rendered as 'koppelgeheim'
- `archive_query_shift_title`: 'Possible change in direction' idiom
- `feed_autopilot_summary_noisy`: 'noisy feeds' idiom rendered as 'drukke feeds'
- `feed_autopilot_reason_opened`: 'engagement' rendered as 'betrokkenheid'
- `oracle_bet_pick_side`: 'Your pick' in betting context
- `oracle_bet_awaiting_hint`: 'Unclear' refers to an unseen button label; may need to match
- `oracle_bet_place`: 'Place bet' - 'Inzet plaatsen' vs 'Wedden'
- `podcast_studio_conflict_title`: 'Stop what is playing?' idiom
- `galaxy_canvas_desc`: 'Knowledge galaxy' feature term rendered as 'sterrenstelsel'
- `commute_unlock_deepdive`: 'Deep Dive' rendered as 'Verdieping' here but kept as AI Deep Dive elsewhere
- `brain_rpg_wrapped_title`: 'Brain Wrapped' pun kept untranslated
- `quote_card_template_dark`: 'Dark Studio' template name translated

## nn (42 chuỗi)
- `tonal_elevation`: Material term 'tonal elevation'; 'Tonal høgd' is a literal rendering, no settled Nynorsk term
- `flow_page`: 'Flow page' ambiguous; rendered 'Straumside'
- `fever_desc`: 'Deprecated' rendered 'Utdatert'; fine but unsure of the UI convention
- `self_hosted`: 'Self-Hosted' rendered 'Eigendrive', unusual compound; 'Sjølvdrive' also possible
- `brain_rpg_streak`: 'Streak' rendered 'rekkje' (e.g. '3 dagars rekkje'); not a common established term
- `brain_rpg_category_business`: 'Business &amp; Finance' rendered 'Næringsliv &amp; økonomi'; wording choice
- `zen_sound_binaural`: Technical audio term kept close to English
- `story_clustering_title`: 'Clustering/Deduplication' rendered 'Sakgruppering/fjerning av duplikat'
- `source_dna_disclaimer`: 'Heuristic' rendered 'Heuristikk'; technical and long
- `headline_signals_desc`: Long technical text with 'lexical', 'hype', 'political bias'; wording uncertain
- `headline_signals_tone_desc_positive`: 'Lexical' rendered 'leksikalsk'; accessibility string, technical
- `bounty_sheet_title`: 'Entity' rendered 'entitet'; 'Agent for undersøking av entitetar' is stiff
- `echo_disclaimer`: Dense technical wording (lexical evidence, author-intent claim)
- `echo_cue_exclamation`: 'Punctuation or uppercase emphasis' rendered as 'Utropsteikn eller utheving med store bokstavar'; slightly narrower than source
- `echo_no_passages`: 'Hype threshold' rendered 'hype-grensa'; loanword
- `dossier_names_note`: 'Named-entity recognition' rendered 'gjenkjenning av namngjevne entitetar'
- `local_sync_pairing_limits`: 'Forward secret' (cryptographic term) rendered 'forover-hemmeleghald'; no established Nynorsk term
- `local_sync_monotonic_warning`: Read state only moves one direction; wording of 'lesen/ulesen/stjerna' adjective forms
- `local_sync_error_secret`: 'lowercase hex' rendered 'små heksadesimale teikn'
- `local_sync_pairing_secret`: 'Pairing secret' rendered 'paringshemmelegheit'; calque
- `local_sync_peer_unconfirmed`: Idiom 'receive once' rendered literally
- `archive_query_start_date`: Date format hint YYYY-MM-DD localized to ÅÅÅÅ-MM-DD, but the app's validator and error string may require the literal format
- `archive_query_end_date`: Same as archive_query_start_date: ÅÅÅÅ-MM-DD placeholder localized
- `archive_query_problem_bad_date`: Same: localized date format hint ÅÅÅÅ-MM-DD
- `feed_autopilot_title`: Feature name; translated 'Kanalautopilot', could be kept as 'Feed Autopilot'
- `feed_zero_triage_title`: Feature name 'Feed Zero Triage' rendered loosely as 'Kanalopprydding til null'
- `oracle_bet_place`: 'Bet' rendered 'veddemål'; gambling connotation, could be 'sats'
- `oracle_bet_pick_side`: 'Your pick' rendered 'Ditt val'
- `oracle_due_evidence_title`: 'Related articles in your news' awkward in English too
- `commute_autoplay_permission_denied`: Android permission name 'Nearby devices' must match system Nynorsk label 'Einingar i nærleiken'
- `curated_bundle_result`: 'failed' rendered 'mislukka' in compact list string
- `galaxy_hint`: 'pinch to zoom' rendered 'knip for å zoome'
- `second_screen_title`: Feature name 'Second Screen' rendered 'Andreskjerm'
- `second_brain_title`: Feature name 'Second Brain' rendered 'Andre hjerne'; may be kept in English
- `notebook_empty_subtitle`: 'Second Brain' rendered 'den andre hjernen din'
- `brain_rpg_wrapped_title`: 'Brain Wrapped' (Spotify-Wrapped pun) rendered 'Vekeoppsummering for hjernen'
- `brain_wrapped_header`: Same as brain_rpg_wrapped_title
- `brain_wrapped_share_title`: Same as brain_rpg_wrapped_title
- `tos_tips`: Source says 'RSS Bear Hub' (likely stale brand); kept as in source
- `preview_article_title`: Original used straight quotes; replaced with guillemets «», and 'Lord of the Mysteries' kept in English as a title
- `zen_daily_edition_enabled`: 'Batch Delivery Mode' rendered 'Samlelevering'
- `bionic_reading_desc`: 'skim' rendered 'skumme'; ok but idiomatic uncertainty

## ro (18 chuỗi)
- `vip_remaining_short`: Shortened to 'z'/'h' abbreviations for days/hours; may read terse
- `zen_wpm_label`: WPM rendered as 'cuv./min' (words per minute); check space on UI
- `zen_sound_pink_noise`: 'Soft Focus Noise' rendered freely as 'Zgomot de concentrare blând'
- `story_cluster_read_main`: 'Lead Article' rendered as 'articolul principal'
- `source_dna_title`: 'Source DNA' translated as 'ADN-ul sursei', branding-like term
- `echo_details_title`: 'Body Language Signals' interpreted as wording signals ('Semnale de limbaj')
- `echo_hype_warning`: 'Hype' left as natural wording; echo_no_passages keeps 'Hype' as threshold name
- `echo_dual_share_text`: Hashtag #Debate left in English
- `local_sync_pairing_limits`: 'forward secret' technical term rendered 'secretizare înainte (forward secrecy)'
- `local_sync_monotonic_warning`: Ambiguous 'starred' phrasing; 'cu stea' used for starred
- `galaxy_hint`: Gesture wording (pinch/drag) idiom
- `triage_inbox_zero_title`: 'Inbox Zero' kept as a concept name
- `feed_zero_triage_title`: 'Feed Zero Triage' feature name partly kept in English
- `oracle_bet_awaiting_hint`: 'Unclear' refers to a result button label; must match the button translation elsewhere
- `oracle_bet_pick_side`: 'Your pick' ambiguous (choice of outcome side)
- `dossier_names_note`: 'named-entity recognition' translated descriptively
- `bounty_sheet_title`: 'Entity Investigation Agent' wording
- `brain_rpg_xp_retained`: 'Base' prefix terse; context unclear

## sl (20 chuỗi)
- `zen_sound_binaural`: Binavralni 40 Hz gama: rare technical term, ensure term 'binavralni' is acceptable
- `local_sync_pairing_limits`: 'forward secret' rendered as 'popolna tajnost naprej' - crypto term, uncertain Slovenian equivalent
- `echo_details_title`: 'Body Language Signals' ambiguous; rendered 'Signali govorice besedila'
- `echo_disclaimer`: 'lexical evidence' translated as 'besedni dokazi'; idiomatic quote marks used
- `echo_contrast_limitation`: 'contrarian headline wording' rendered as 'nasprotujoče besedilo naslovov'
- `bounty_sheet_title`: 'Entity Investigation Agent': 'entiteta' is stiff but standard
- `dossier_names_note`: 'named-entity recognition' rendered as 'prepoznavanje imenovanih entitet'
- `feed_autopilot_title`: 'Autopilot' translated as 'Samodejni pilot'; may prefer keeping Autopilot
- `feed_zero_triage_title`: 'Feed Zero Triage' idiom; rendered 'Pregled do ničle'
- `second_brain_title`: 'Second Brain' concept name translated as 'Drugi možgani'
- `second_brain_zoom_hint`: Gesture wording (pinch/pan) may differ from platform convention
- `bionic_reading_desc`: 'Bold the first letters' phrasing awkward in Slovenian
- `vip_remaining_short`: 'd' and 'h' abbreviations used for days/hours; Slovenian plural for days not handled
- `vip_success_message`: 'dni' plural form fixed, wrong for 1 or 2 days
- `source_dna_action`: 'Source DNA' translated 'DNK vira' (DNA = DNK in Slovenian)
- `zen_wpm_label`: WPM kept as English abbreviation
- `oracle_bet_awaiting_hint`: 'Unclear' refers to a button label; wording 'nejasno' must match the button
- `brain_rpg_xp_retained`: 'retained' rendered 'ohranjeno'; fine but decay concept is niche
- `archive_query_start_date`: Date format YYYY-MM-DD localized to LLLL-MM-DD; code parser expects YYYY may confuse
- `archive_query_problem_bad_date`: Format token localized to LLLL-MM-DD; verify intended

## sr (17 chuỗi)
- `zen_sound_bowl`: Singing bowl rendered as 'певајућа посуда'; no settled Serbian term
- `zen_sound_binaural`: Gamma kept as 'гама' with 40Hz token
- `tips_top_bar_tonal_elevation`: 'Tonal elevation' rendered as 'тонска висина'; ambiguous UI term
- `echo_details_title`: 'Body Language Signals' means article body text; rendered 'Сигнали језика текста'
- `echo_cue_exclamation`: Long phrase; check fit
- `local_sync_pairing_limits`: 'forward secret' rendered 'тајност унапред'; rare crypto term
- `local_sync_monotonic_warning`: Awkward concept; 'прочитано и са звездицом могу се проширити'
- `local_sync_wait_reply`: Ambiguous English; guessed meaning
- `local_sync_role_hint`: Role of Send/Receive ambiguous; Receive translated 'Пријем'
- `oracle_bet_closed_no_bet`: 'Клађење' for betting; gambling tone may suit or not
- `oracle_bet_place`: Short button 'Уложи'; maybe 'Постави улог'
- `vip_remaining_short`: Abbreviated 'д.'/'ч.' for days/hours; may be unclear
- `highlights_fallback_badge`: 'Offline Smart' rendered 'Паметно, ван мреже'
- `bionic_reading_desc`: 'skim' rendered 'прелетели текст'
- `widget_article_pin_unsupported`: 'pinning' rendered 'качење'; Serbian term uncertain
- `headline_signals_desc`: Long technical text; 'lexical' and 'hype' terms
- `dossier_names_note`: Named-entity recognition phrasing

## sv (12 chuỗi)
- `echo_cue_exclamation`: Ambiguous English 'Punctuation or uppercase emphasis'; rendered literally.
- `zen_sound_binaural`: Technical audio term; 'Binauralt 40 Hz gamma' may need a native check.
- `source_dna_disclaimer`: Heuristic/attribution wording is technical; phrasing may be stiff.
- `headline_signals_tone_desc_positive`: 'Lexical' tone is a rare technical term; 'lexikal' may sound stiff.
- `dossier_names_note`: 'Named-entity recognition' rendered as 'igenkänning av namngivna entiteter'; term is niche.
- `oracle_bet_pick_side`: 'Your pick' in betting context; 'Ditt val' is generic.
- `local_sync_pairing_limits`: 'Forward secret' rendered as 'framåtsekretess'; crypto term.
- `local_sync_monotonic_warning`: Abstract idea of monotonic state; 'läsläget går bara framåt' is a paraphrase.
- `feed_autopilot_action_snooze`: 'Snooze' has no settled Swedish UI term; 'Snooza' is a loanword.
- `brain_rpg_decay_notification_body`: 'Retained XP' and decay concept; phrasing may be awkward.
- `brain_rpg_xp_retained`: 'Base XP ... retained' short and ambiguous.
- `story_clustering_desc`: 'Breaking news' rendered as 'senaste nytt'; could be 'nyhetsflash'.

## tr (13 chuỗi)
- `zen_sound_binaural`: Binaural 40Hz Gama: technical term, Turkish 'gama' may be unfamiliar
- `oracle_bet_place`: 'bet' rendered as bahis; virtual-points context, could be softer (tahmin puanı)
- `oracle_bet_place_summary`: Word order and 'yatır' for stake with %2$s side; check natural reading
- `local_sync_monotonic_warning`: Technical idea of monotonic read state; wording stiff
- `local_sync_pairing_limits`: 'forward secret' rendered as 'ileriye dönük gizlilik'; crypto term
- `local_sync_secret_warning`: 'pairing secret' rendered literally as eşleştirme sırrı
- `feed_zero_triage_title`: 'Feed Zero Triage' product-like name, rendered Sıfır Besleme Ayıklama
- `echo_details_title`: 'Body Language Signals' ambiguous (article body text vs body language)
- `dossier_names_note`: named-entity recognition rendered as 'adlandırılmış varlık tanıma'
- `brain_rpg_wrapped_title`: 'Brain Wrapped' idiom (Spotify Wrapped) rendered as 'Brain Özeti'
- `zen_calibration_train_action`: 'Train' menu action rendered as 'Hızı kalibre et'
- `tips_top_bar_tonal_elevation`: 'Tonal height' Material term; rendered as 'ton yüksekliği'
- `auto_branch_commute`: 'Morning briefing' short label, Android Auto context

## Đợt hai: chuỗi một từ (2026-11-25)

Đợt một chỉ lấy chuỗi từ hai chữ trở lên nên bỏ sót 178 chuỗi một từ (Version, Title, Bold, Cancel...). Phát hiện khi chọn tiếng Séc trên S24U thấy "Version" còn tiếng Anh. Đợt hai dịch ~2.400 chuỗi một hay hai từ cho 22 locale, vẫn là dịch máy chưa người duyệt.

### az (4)
- `justify`: Text justify term
- `sync_interval`: Short form
- `story_cluster_lead_badge`: Lead badge term
- `echo_cue_intensifier`: Exaggeration term

### bg (4)
- `sync_interval`: Abbreviated "Синхр."
- `story_cluster_lead_badge`: Lead badge term
- `echo_cue_intensifier`: Exaggeration term
- `justify`: Text justify term

### ca (2)
- `deep_read_grounded_badge`: guessed
- `story_cluster_lead_badge`: Lead meaning unclear

### cs-rCZ (4)
- `synchronous`: Synchronizuji: progressive state
- `story_cluster_lead_badge`: Hlavní: lead badge meaning
- `echo_cue_intensifier`: Přehánění
- `deep_dive_col_area`: Oblast

### da (2)
- `deep_read_grounded_badge`: Kildebaseret guessed
- `story_cluster_lead_badge`: Lead meaning unclear

### eu-rES (3)
- `deep_read_grounded_badge`: guessed
- `galaxy_mode_stars`: Galaxia vs stars
- `feed_autopilot_action_unsubscribe`: phrasing

### fa (1)
- `story_cluster_lead_badge`: 'Lead' as top story badge; picked short word

### hi-rIN (1)
- `story_cluster_lead_badge`: 'Lead' as top story badge; picked short word

### hu (1)
- `story_cluster_lead_badge`: 'Lead' as top story badge; picked short word

### iw (5)
- `story_cluster_lead_badge`: ראשי
- `brain_rpg_stats_attempted`: נוסו
- `local_sync_peer_confirmed`: מצומד
- `echo_cue_intensifier`: הגזמה
- `zen_sleep_timer_off`: רצוף

### kn (5)
- `justify`: Transliterated justify
- `story_cluster_lead_badge`: Lead badge term
- `echo_cue_intensifier`: Exaggeration term
- `deep_read_grounded_badge`: Grounded term
- `synchronous`: Syncing form

### lzh (7)
- `for_2_weeks`: 兩週，同於一週之義
- `agree`: 許 as agree/consent: terse
- `yes`: 然/否 literary yes/no
- `close`: 閉 single-char
- `bounty_action_investigate`: 查
- `echo_cue_intensifier`: 誇飾
- `local_sync_peer_confirmed`: 已配對

### ml (5)
- `justify`: Transliterated justify
- `story_cluster_lead_badge`: Lead badge term
- `echo_cue_intensifier`: Exaggeration term
- `deep_read_grounded_badge`: Grounded term
- `sync_interval`: Short form

### my (4)
- `justify`: Text justify term
- `echo_cue_intensifier`: Exaggeration term
- `story_cluster_lead_badge`: Lead badge term
- `brain_rpg_streak_shield_protected`: Protected term

### nb-rNO (2)
- `deep_read_grounded_badge`: guessed
- `story_cluster_lead_badge`: Lead meaning unclear

### nl (4)
- `story_cluster_lead_badge`: Hoofd
- `brain_rpg_streak_shield_protected`: Beschermd
- `echo_cue_intensifier`: Overdrijving
- `deep_dive_col_area`: Gebied

### nn (3)
- `sync_interval`: Short for Sync interval; "Synk" may be ambiguous
- `story_cluster_lead_badge`: "Leiande" for Lead badge
- `echo_cue_intensifier`: Exaggeration term

### ro (2)
- `deep_read_grounded_badge`: guessed
- `story_cluster_lead_badge`: Lead meaning unclear

### sl (4)
- `synchronous`: Sinhroniziranje
- `story_cluster_lead_badge`: Glavno
- `deep_dive_col_area`: Področje
- `echo_cue_intensifier`: Pretiravanje

### sr (1)
- `story_cluster_lead_badge`: 'Lead' as top story badge; picked short word

### sv (2)
- `deep_read_grounded_badge`: Källbaserad guessed
- `story_cluster_lead_badge`: Lead meaning unclear

### tr (0)



# Đợt 2: 14 locale còn chuỗi tiếng Anh (2026-11-25)

Trước đợt này 14 locale có 6.603 chuỗi y hệt tiếng Anh (đếm chuỗi từ hai chữ trở lên). Đã dịch 6.508 chuỗi bằng mô hình ngôn ngữ (7 lượt, mỗi lượt hai locale; còn lại là URL, tên thương hiệu, hashtag, thuật ngữ `Deep Dive`/`Brain RPG` giữ nguyên). Chưa người bản ngữ nào duyệt; chuỗi một chữ hoặc dưới ba ký tự chữ cái không nằm trong đợt này. Danh sách dưới đây là chỗ bản dịch tự đánh dấu nghi ngờ, không phải toàn bộ chỗ có thể sai.

## uk (9 chuỗi)
- `triage_inbox_zero_title`: Inbox Zero kept as English term
- `bounty_sheet_title`: 'Entity Investigation Agent' - bounty/entity wording
- `echo_hype_warning`: 'Hype' threshold term kept
- `brain_rpg_xp_retained`: 'retained' phrasing
- `zen_sound_binaural`: term kept with 40Hz
- `feed_zero_triage_title`: product-style name partly translated
- `oracle_bet_out_of_points`: daily UTC points wording
- `local_sync_pairing_limits`: 'forward secret' technical term
- `local_sync_role_hint`: send/receive role flow wording

## pl-rPL (8 chuỗi)
- `triage_inbox_zero_title`: Inbox Zero kept as English term
- `bounty_sheet_title`: 'Entity' rendered as encja
- `echo_hype_warning`: 'Hype' threshold term kept
- `dossier_sheet_title`: 'intelligence dossier' as teczka wywiadowcza
- `local_sync_pairing_limits`: 'forward secret' rendered as poufność z wyprzedzeniem
- `feed_zero_triage_title`: product-style name partly translated
- `local_sync_role_hint`: send/receive role flow wording
- `echo_details_title`: 'Body Language Signals' interpreted as text wording signals

## it-rIT (9 chuỗi)
- `mindmap_node_details`: 'Copy Insight' - copia spunto, sense ambiguous
- `brain_rpg_xp_retained`: 'Base XP · % retained' phrasing
- `echo_hype_warning`: 'Hype' term kept in echo_no_passages
- `source_dna_action`: DNA della fonte metaphor
- `oracle_bet_awaiting_hint`: 'Unclear' refers to a button label; translated as Incerto
- `archive_query_limits`: 'Vietnamese' kept as in English source
- `local_sync_pairing_limits`: 'forward secret' technical term
- `local_sync_monotonic_warning`: read/starred wording (preferito for starred)
- `zen_sound_binaural`: Gamma binaurale 40Hz - technical

## in-rID (8 chuỗi)
- `brain_rpg_xp_retained`: 'Base XP · % retained' phrasing
- `echo_no_passages`: 'Hype' term kept as feature name
- `oracle_bet_awaiting_hint`: 'Unclear' is a button label; translated Tidak jelas
- `local_sync_pairing_limits`: 'forward secrecy' technical term left in English
- `dossier_create_action`: 'dossier' translated as dosir
- `feed_zero_triage_title`: 'Feed Zero Triage' - Triase Feed Nol
- `triage_inbox_zero_title`: 'Inbox Zero' kept in English
- `zen_sound_binaural`: technical

## ar (9 chuỗi)
- `triage_inbox_zero_title`: Inbox Zero rendered as 'empty inbox'
- `feed_zero_triage_title`: 'Feed Zero' concept, translated literally
- `source_dna_action`: DNA metaphor translated literally
- `echo_details_title`: 'Body Language Signals' interpreted as wording signals
- `brain_rpg_wrapped_title`: 'Wrapped' rendered as 'ملخص'
- `second_brain_title`: 'Second Brain' translated literally
- `commute_cast_subtitle`: '2-Host Morning Radio Overview' paraphrased
- `local_sync_pairing_limits`: 'forward secret' rendered as 'السرية المستقبلية'
- `local_sync_monotonic_warning`: monotonic read-state wording is technical

## pt-rBR (7 chuỗi)
- `feed_autopilot_title`: 'Autopilot' rendered as 'Piloto automático'
- `local_sync_pairing_limits`: 'forward secret' rendered as 'sigilo futuro'
- `local_sync_monotonic_warning`: monotonic read-state wording is technical
- `echo_details_title`: 'Body Language Signals' interpreted as text-language signals
- `brain_rpg_wrapped_title`: 'Wrapped' rendered as 'Resumo'
- `commute_cast_subtitle`: '2-Host Morning Radio Overview' paraphrased
- `oracle_bet_awaiting_hint`: 'Unclear' quoted with guillemets, check UI label match

## pt (8 chuỗi)
- `vip_buy_30d_locked`: "brevemente" for coming soon
- `brain_rpg_xp_retained`: retained XP wording
- `bionic_reading_desc`: "skim" -> ler na diagonal
- `echo_disclaimer`: Hype/lexical evidence wording
- `dossier_create_action`: dossier -> dossiê (BR spelling) maybe dossier in PT-PT
- `oracle_bet_awaiting_hint`: "Unclear" button label must match the option label in app
- `local_sync_pairing_limits`: forward secret -> sigilo de encaminhamento technical term
- `brain_rpg_title`: Brain RPG kept as brand-like name

## ru-rRU (9 chuỗi)
- `brain_rpg_xp_retained`: retained XP phrasing
- `brain_rpg_decay_notification_body`: Practice: %2$s grammar with placeholder
- `echo_disclaimer`: Unclear/Hype wording must match option labels
- `oracle_bet_awaiting_hint`: Unclear option label must match app
- `local_sync_pairing_limits`: forward secret technical term
- `streak_rescue_message`: plural forms of days with %d
- `bionic_reading_desc`: skim faster idiom
- `commute_unlock_deepdive`: Deep Dive translated as razbor
- `zen_settings_title`: Zen Focus wording

## es-rES (10 chuỗi)
- `brain_rpg_title`: Brand-like name kept in English
- `zen_sound_binaural`: Technical term 'Binaural 40Hz Gamma'
- `quote_card_template_dark`: Template name 'Dark Studio'
- `echo_hype_warning`: 'Hype' translated as exaggerated/promotional
- `echo_no_passages`: 'Hype threshold' rendered as umbral de exageración
- `local_sync_pairing_limits`: 'forward secret' rendered as 'secreto perfecto hacia adelante'
- `oracle_bet_awaiting_hint`: 'Unclear' refers to a button label; quoted as «No claro»
- `echo_disclaimer`: 'Unclear' refers to a label; quoted as «No claro»
- `archive_query_limits`: English and Vietnamese kept as languages
- `zen_calibration_questions_header`: Comprehension check wording

## zh-rTW (13 chuỗi)
- `brain_rpg_title`: Brand-like name kept in English
- `zen_sound_binaural`: Technical term binaural gamma
- `zen_speed_reading`: RSVP kept as technical term
- `source_dna_action`: 'Source DNA' feature name; DNA kept
- `echo_hype_warning`: 'Hype' rendered as promotional/exaggerated
- `echo_no_passages`: 'Hype threshold' rendered as 誇大門檻
- `headline_signals_title`: 'Clickbait' rendered as 釣魚標題 (Taiwan usage: 標題黨/釣魚標題)
- `local_sync_pairing_limits`: 'forward secret' rendered as 前向保密
- `oracle_bet_awaiting_hint`: 'Unclear' refers to a button label, quoted as 「不明確」
- `bounty_sheet_title`: 'Entity Investigation Agent' wording
- `bounty_stats_summary`: Bullet separator kept
- `curated_bundles_title`: 'bundles' rendered as 組合
- `quote_card_template_dark`: Template name 'Dark Studio'

## de-rDE (6 chuỗi)
- `echo_disclaimer`: 'Unclear' vs 'Unklar' must match UI label; unsure of term
- `dossier_names_note`: named-entity recognition rendered literally
- `local_sync_pairing_limits`: 'forward secret' kept as Forward Secrecy
- `zen_sound_pink_noise`: 'Soft Focus Noise' coined name
- `oracle_bet_pick_side`: 'Your pick' ambiguous in betting context
- `story_clustering_title`: AI Deduplication wording

## ja (7 chuỗi)
- `echo_disclaimer`: 'Unclear' rendered as 不明; must match UI label
- `dossier_sheet_title`: 'intelligence dossier' loanword ドシエ
- `dossier_names_note`: named-entity recognition rendered as 固有表現認識
- `local_sync_pairing_limits`: 'forward secret' rendered as 前方秘匿性
- `zen_sound_pink_noise`: 'Soft Focus Noise' coined name
- `oracle_bet_pick_side`: 'Your pick' ambiguous in betting context
- `galaxy_canvas_desc`: 'Knowledge galaxy' feature name

## fr-rFR (7 chuỗi)
- `echo_dual_share_text`: hashtags, kept
- `bounty_sheet_title`: 'Entity Investigation Agent' product term
- `echo_disclaimer`: 'Unclear' must match UI label for the unclear option
- `brain_rpg_title`: brand-like name kept
- `zen_sound_binaural`: technical term
- `echo_hype_warning`: 'Hype' threshold term kept English
- `local_sync_pairing_limits`: 'forward secret' crypto term

## zh-rCN (7 chuỗi)
- `echo_dual_share_text`: hashtags, kept
- `echo_hype_warning`: 'Hype' threshold term
- `echo_no_passages`: 'Hype' kept as UI label
- `local_sync_pairing_limits`: 'forward secret' crypto term
- `feed_autopilot_title`: 'Autopilot' rendered as 自动驾驶
- `brain_rpg_title`: brand-like name kept
- `oracle_bet_balance`: 'points' unit 点 vs 积分
