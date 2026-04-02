import os, glob

translations = {
    'values-ar': {
        'unlock': 'فتح مظهر AMOLED',
        'desc': 'شاهد إعلان فيديو قصير لفتح المظهر الأسود الحقيقي AMOLED بشكل دائم.',
        'watch': 'مشاهدة الإعلان',
        'not_ready': 'الإعلان غير جاهز. يرجى المحاولة مرة أخرى لاحقًا.'
    },
    'values-az': {
        'unlock': 'AMOLED Mövzusunu Açın',
        'desc': 'Həqiqi qara AMOLED mövzusunu həmişəlik açmaq üçün qısa video reklama baxın.',
        'watch': 'Reklama Bax',
        'not_ready': 'Reklam hazır deyil. Zəhmət olmasa, daha sonra yenidən cəhd edin.'
    },
    'values-bg': {
        'unlock': 'Отключване на AMOLED тема',
        'desc': 'Гледайте кратка видео реклама, за да отключите трайно истинската черна AMOLED тема.',
        'watch': 'Гледайте реклама',
        'not_ready': 'Рекламата не е готова. Моля, опитайте отново по-късно.'
    },
    'values-ca': {
        'unlock': 'Desbloquejar tema AMOLED',
        'desc': 'Mireu un breu anunci de vídeo per desbloquejar permanentment el veritable tema fosc fosc AMOLED.',
        'watch': 'Veure anunci',
        'not_ready': 'L\'anunci no està preparat. Si us plau, torneu-ho a provar més tard.'
    },
    'values-cs-rCZ': {
        'unlock': 'Odemknout téma AMOLED',
        'desc': 'Podívejte se na krátkou videoreklamu a trvale odemkněte skutečně černé téma AMOLED.',
        'watch': 'Sledovat reklamu',
        'not_ready': 'Reklama není připravena. Zkuste to prosím znovu později.'
    },
    'values-da': {
        'unlock': 'Lås AMOLED-tema op',
        'desc': 'Se en kort videoannonce for at låse op for det sande sorte AMOLED-tema permanent.',
        'watch': 'Se annonce',
        'not_ready': 'Annoncen er ikke klar. Prøv igen senere.'
    },
    'values-de-rDE': {
        'unlock': 'AMOLED-Design freischalten',
        'desc': 'Sehen Sie sich eine kurze Videoanzeige an, um das echte schwarze AMOLED-Design dauerhaft freizuschalten.',
        'watch': 'Werbung ansehen',
        'not_ready': 'Die Anzeige ist noch nicht bereit. Bitte versuchen Sie es später noch einmal.'
    },
    'values-es-rES': {
        'unlock': 'Desbloquear tema AMOLED',
        'desc': 'Mire un breve anuncio de video para desbloquear permanentemente el tema AMOLED negro real.',
        'watch': 'Ver anuncio',
        'not_ready': 'El anuncio no está listo. Por favor, inténtelo de nuevo más tarde.'
    },
    'values-eu-rES': {
        'unlock': 'Desblokeatu AMOLED gaia',
        'desc': 'Ikusi bideo labur bat AMOLED gai beltz benetakoa betirako desblokeatzeko.',
        'watch': 'Ikusi iragarkia',
        'not_ready': 'Iragarkia ez dago prest. Mesedez, saiatu berriro geroago.'
    },
    'values-fa': {
        'unlock': 'باز کردن تم AMOLED',
        'desc': 'برای باز کردن قفل دائمی تم سیاه واقعی AMOLED، یک تبلیغ ویدیویی کوتاه تماشا کنید.',
        'watch': 'تماشای تبلیغ',
        'not_ready': 'تبلیغ آماده نیست. لطفا بعدا دوباره امتحان کنید.'
    },
    'values-fr-rFR': {
        'unlock': 'Débloquer le thème AMOLED',
        'desc': 'Regardez une courte publicité vidéo pour débloquer de façon permanente le vrai thème noir AMOLED.',
        'watch': 'Regarder la pub',
        'not_ready': 'L\'annonce n\'est pas prête. Veuillez réessayer plus tard.'
    },
    'values-hi-rIN': {
        'unlock': 'AMOLED थीम अनलॉक करें',
        'desc': 'सच्चे काले AMOLED थीम को स्थायी रूप से अनलॉक करने के लिए एक छोटा वीडियो विज्ञापन देखें।',
        'watch': 'विज्ञापन देखें',
        'not_ready': 'विज्ञापन अभी तैयार नहीं है। कृपया बाद में पुनः प्रयास करें।'
    },
    'values-hu': {
        'unlock': 'AMOLED téma feloldása',
        'desc': 'Nézzen meg egy rövid videóhirdetést, hogy véglegesen feloldja a valódi fekete AMOLED témát.',
        'watch': 'Hirdetés megtekintése',
        'not_ready': 'A hirdetés nem áll készen. Kérjük, próbálja újra később.'
    },
    'values-in-rID': {
        'unlock': 'Buka Kunci Tema AMOLED',
        'desc': 'Tonton iklan video singkat untuk membuka kunci tema hitam AMOLED secara permanen.',
        'watch': 'Tonton Iklan',
        'not_ready': 'Iklan belum siap. Silakan coba lagi nanti.'
    },
    'values-it-rIT': {
        'unlock': 'Sblocca il tema AMOLED',
        'desc': 'Guarda un breve annuncio video per sbloccare in modo permanente il vero tema nero AMOLED.',
        'watch': 'Guarda l\'annuncio',
        'not_ready': 'L\'annuncio non è pronto. Riprova più tardi.'
    },
    'values-iw': {
        'unlock': 'בטל נעילת ערכת נושא AMOLED',
        'desc': 'צפה במודעת וידאו קצרה כדי לבטל את הנעילה של ערכת הנושא השחורה האמיתית של AMOLED.',
        'watch': 'צפה במודעה',
        'not_ready': 'המודעה אינה מוכנה. אנא נסה שוב מאוחר יותר.'
    },
    'values-ja': {
        'unlock': 'AMOLEDテーマをロック解除',
        'desc': '短い動画広告を視聴して、真の黒のAMOLEDテーマを永久にロック解除します。',
        'watch': '広告を見る',
        'not_ready': '広告の準備ができていません。後でもう一度お試しください。'
    },
    'values-kn': {
        'unlock': 'AMOLED ಥೀಮ್ ಅನ್‌ಲಾಕ್ ಮಾಡಿ',
        'desc': 'ನೈಜ ಕಪ್ಪು AMOLED ಥೀಮ್ ಅನ್ನು ಶಾಶ್ವತವಾಗಿ ಅನ್‌ಲಾಕ್ ಮಾಡಲು ಸಣ್ಣ ವೀಡಿಯೊ ಜಾಹೀರಾತನ್ನು ವೀಕ್ಷಿಸಿ.',
        'watch': 'ಜಾಹೀರಾತು ವೀಕ್ಷಿಸಿ',
        'not_ready': 'ಜಾಹೀರಾತು ಸಿದ್ಧವಾಗಿಲ್ಲ. ದಯವಿಟ್ಟು ನಂತರ ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.'
    },
    'values-lzh': {
        'unlock': '解鎖AMOLED主題',
        'desc': '觀看短片廣告以永久解鎖純黑AMOLED主題。',
        'watch': '觀看廣告',
        'not_ready': '廣告未準備好。請稍後再試。'
    },
    'values-ml': {
        'unlock': 'AMOLED തീം അൺലോക്കുചെയ്യുക',
        'desc': 'യഥാർത്ഥ കറുത്ത AMOLED തീം ശാശ്വതമായി അൺലോക്കുചെയ്യാൻ ഒരു ചെറിയ വീഡിയോ പരസ്യം കാണുക.',
        'watch': 'പരസ്യം കാണുക',
        'not_ready': 'പരസ്യം തയ്യാറായിട്ടില്ല. ദയവായി കുറച്ച് കഴിഞ്ഞ് വീണ്ടും ശ്രമിക്കുക.'
    },
    'values-my': {
        'unlock': 'AMOLED အပြင်အဆင်ကို ဖွင့်ပါ',
        'desc': 'စစ်မှန်သော အနက်ရောင် AMOLED အပြင်အဆင်ကို အမြဲတမ်းဖွင့်ရန် ဗီဒီယိုကြော်ငြာတိုလေးကို ကြည့်ပါ။',
        'watch': 'ကြော်ငြာကြည့်ရန်',
        'not_ready': 'ကြော်ငြာအသင့်မဖြစ်သေးပါ။ ကျေးဇူးပြု၍ ခဏနေမှ ထပ်ကြိုးစားပါ။'
    },
    'values-nb-rNO': {
        'unlock': 'Lås opp AMOLED-tema',
        'desc': 'Se en kort videoannonse for å permanent låse opp det sanne svarte AMOLED-temaet.',
        'watch': 'Se annonse',
        'not_ready': 'Annonsen er ikke klar. Vennligst prøv igjen senere.'
    },
    'values-nl': {
        'unlock': 'Ontgrendel AMOLED-thema',
        'desc': 'Bekijk een korte videoadvertentie om het ware zwarte AMOLED-thema permanent te ontgrendelen.',
        'watch': 'Bekijk advertentie',
        'not_ready': 'Advertentie is niet klaar. Probeer het later opnieuw.'
    },
    'values-nn': {
        'unlock': 'Lås opp AMOLED-tema',
        'desc': 'Sjå ein kort videoannonse for å permanent låse opp det sanne svarte AMOLED-temaet.',
        'watch': 'Sjå annonse',
        'not_ready': 'Annonsen er ikkje klar. Ver vennleg å prøv igjen seinare.'
    },
    'values-pl-rPL': {
        'unlock': 'Odblokuj motyw AMOLED',
        'desc': 'Obejrzyj krótką reklamę wideo, aby trwale odblokować prawdziwie czarny motyw AMOLED.',
        'watch': 'Obejrzyj reklamę',
        'not_ready': 'Reklama nie jest gotowa. Spróbuj ponownie później.'
    },
    'values-pt': {
        'unlock': 'Desbloquear tema AMOLED',
        'desc': 'Assista a um pequeno anúncio em vídeo para desbloquear permanentemente o verdadeiro tema preto AMOLED.',
        'watch': 'Assistir ao anúncio',
        'not_ready': 'O anúncio não está pronto. Por favor, tente novamente mais tarde.'
    },
    'values-pt-rBR': {
        'unlock': 'Desbloquear tema AMOLED',
        'desc': 'Assista a um vídeo curto para desbloquear permanentemente o tema AMOLED preto verdadeiro.',
        'watch': 'Assistir anúncio',
        'not_ready': 'O anúncio não está pronto. Tente novamente mais tarde.'
    },
    'values-ro': {
        'unlock': 'Deblochează tema AMOLED',
        'desc': 'Urmăriți un scurt anunț video pentru a debloca permanent tema AMOLED negru adevărat.',
        'watch': 'Vezi reclamă',
        'not_ready': 'Anunțul nu este gata. Vă rugăm să încercați din nou mai târziu.'
    },
    'values-ru-rRU': {
        'unlock': 'Разблокировать AMOLED тему',
        'desc': 'Посмотрите короткую видеорекламу, чтобы навсегда разблокировать настоящую черную AMOLED тему.',
        'watch': 'Смотреть рекламу',
        'not_ready': 'Реклама не готова. Пожалуйста, повторите попытку позже.'
    },
    'values-sl': {
        'unlock': 'Odkleni temo AMOLED',
        'desc': 'Oglejte si kratek video oglas, da trajno odklenete pravo črno temo AMOLED.',
        'watch': 'Oglejte si oglas',
        'not_ready': 'Oglas ni pripravljen. Prosimo, poskusite znova pozneje.'
    },
    'values-sr': {
        'unlock': 'Откључај АМОЛЕД тему',
        'desc': 'Погледајте кратак видео оглас да бисте трајно откључали праву црну АМОЛЕД тему.',
        'watch': 'Погледај оглас',
        'not_ready': 'Оглас није спреман. Покушајте поново касније.'
    },
    'values-sv': {
        'unlock': 'Lås upp AMOLED-tema',
        'desc': 'Titta på en kort videoannons för att permanent låsa upp det sanna svarta AMOLED-temat.',
        'watch': 'Titta på annons',
        'not_ready': 'Annonsen är inte klar. Försök igen senare.'
    },
    'values-tr': {
        'unlock': 'AMOLED Temasının Kilidini Aç',
        'desc': 'Gerçek siyah AMOLED temasının kilidini kalıcı olarak açmak için kısa bir video reklam izleyin.',
        'watch': 'Reklamı izle',
        'not_ready': 'Reklam hazır değil. Lütfen daha sonra tekrar deneyin.'
    },
    'values-uk': {
        'unlock': 'Розблокувати тему AMOLED',
        'desc': 'Подивіться коротку відеорекламу, щоб назавжди розблокувати справжню чорну тему AMOLED.',
        'watch': 'Дивитись рекламу',
        'not_ready': 'Реклама не готова. Будь ласка, спробуйте пізніше.'
    },
    'values-zh-rCN': {
        'unlock': '解锁 AMOLED 主题',
        'desc': '观看一段简短的视频广告以永久解锁真正的纯黑 AMOLED 主题。',
        'watch': '观看广告',
        'not_ready': '广告未准备好。请稍后再试。'
    },
    'values-zh-rTW': {
        'unlock': '解鎖 AMOLED 主題',
        'desc': '觀看一段簡短的視頻廣告以永久解鎖真正的純黑 AMOLED 主題。',
        'watch': '觀看廣告',
        'not_ready': '廣告未準備好。請稍後再試。'
    }
}

import re
res_dir = 'app/src/main/res'
xml_files = glob.glob(os.path.join(res_dir, 'values-*/strings.xml'))

count = 0
for file in xml_files:
    dir_name = os.path.basename(os.path.dirname(file))
    if dir_name not in translations:
        continue
        
    t = translations[dir_name]
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()

    # Replace the english stubs we added previously
    content = re.sub(r'<string name="unlock_amoled_theme">.*?</string>', f'<string name="unlock_amoled_theme">{t["unlock"]}</string>', content)
    content = re.sub(r'<string name="unlock_amoled_theme_desc">.*?</string>', f'<string name="unlock_amoled_theme_desc">{t["desc"]}</string>', content)
    content = re.sub(r'<string name="watch_ad">.*?</string>', f'<string name="watch_ad">{t["watch"]}</string>', content)
    content = re.sub(r'<string name="ad_not_ready">.*?</string>', f'<string name="ad_not_ready">{t["not_ready"]}</string>', content)

    with open(file, 'w', encoding='utf-8') as f:
        f.write(content)
    
    count += 1

print(f"Successfully translated {count} languages!")
