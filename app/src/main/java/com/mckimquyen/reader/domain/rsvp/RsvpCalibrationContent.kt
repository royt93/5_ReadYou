package com.mckimquyen.reader.domain.rsvp

data class CalibrationQuestion(val prompt: String, val options: List<String>, val correctIndex: Int)

data class CalibrationPassage(val text: String, val questions: List<CalibrationQuestion>)

/**
 * Static reading passages for the speed calibration. They live in Kotlin rather than strings.xml because every
 * question belongs to one specific passage; only the UI around them is localized. Other languages read the English set.
 */
object RsvpCalibrationContent {

    fun forLanguage(languageTag: String): List<CalibrationPassage> =
        if (languageTag.lowercase().startsWith("vi")) VI else EN

    private val EN = listOf(
        CalibrationPassage(
            text = "Marta runs the only bakery in the village of Alder Bay. She wakes at four every morning and lights the stone oven before the streets are awake. By six she has baked thirty-six loaves of rye bread and twelve trays of cardamom buns. She loads them onto a blue cart and pulls it to the harbor, where the fishing crews wait. Most customers pay with coins, but the ferry captain always pays with a jar of pickled plums. Marta keeps every jar on a high shelf beside the clock.",
            questions = listOf(
                CalibrationQuestion("What color is the cart Marta uses?", listOf("Red", "Blue", "Green", "Yellow"), 1),
                CalibrationQuestion("How does the ferry captain pay?", listOf("With coins", "With fresh fish", "With a jar of pickled plums", "With a promise"), 2),
            ),
        ),
        CalibrationPassage(
            text = "Tomas repairs old clocks in a narrow shop above a tailor. On Tuesdays he opens at noon, and on all other days at nine. His favorite clock is a brass one with a small silver bird that pops out on the hour. Last winter a student brought it in because the bird had stopped singing. Tomas found a bent spring no wider than a hair and fixed it in forty minutes. The student paid with a hand-knitted scarf, which Tomas still wears when the shop is cold.",
            questions = listOf(
                CalibrationQuestion("When does Tomas open the shop on Tuesdays?", listOf("At nine", "At noon", "At six", "At three"), 1),
                CalibrationQuestion("What did the student give Tomas as payment?", listOf("A scarf", "A book", "A silver coin", "A bird"), 0),
            ),
        ),
        CalibrationPassage(
            text = "A group of seven hikers set out from Pine Ridge at dawn to cross the northern pass. The first hour was easy, following a wide path beside a quiet stream. Then the trail climbed sharply and the wind grew colder. Around noon they stopped at a stone hut and shared a pot of lentil soup. Nobody wanted to leave, but clouds were gathering in the west. They reached the far side at five, tired but pleased, and counted every member twice before pitching their tents beside a small lake.",
            questions = listOf(
                CalibrationQuestion("How many hikers were in the group?", listOf("Five", "Six", "Seven", "Nine"), 2),
                CalibrationQuestion("What did the hikers eat at the stone hut?", listOf("Lentil soup", "Bread and cheese", "Rice", "Fresh berries"), 0),
            ),
        ),
    )

    private val VI = listOf(
        CalibrationPassage(
            text = "Bà Lan bán bánh mì ở chợ nhỏ cuối làng Cồn Sơn. Mỗi sáng bà thức dậy lúc bốn giờ để nhóm lò than và nhào bột. Đến sáu giờ bà đã nướng xong ba mươi sáu ổ bánh mì và mười hai khay bánh bông lan nhỏ. Bà xếp tất cả lên chiếc xe đẩy màu xanh rồi đẩy ra bến sông, nơi các ghe chài đang chờ. Phần lớn khách trả bằng tiền lẻ, nhưng ông lái đò lúc nào cũng trả bằng một hũ mận muối. Bà để mọi hũ lên chiếc kệ cao cạnh đồng hồ.",
            questions = listOf(
                CalibrationQuestion("Chiếc xe đẩy của bà Lan có màu gì?", listOf("Màu đỏ", "Màu xanh", "Màu vàng", "Màu trắng"), 1),
                CalibrationQuestion("Ông lái đò trả tiền bánh bằng gì?", listOf("Bằng tiền lẻ", "Bằng cá tươi", "Bằng một hũ mận muối", "Bằng lời hứa"), 2),
            ),
        ),
        CalibrationPassage(
            text = "Chú Tâm sửa đồng hồ cũ trong một cửa tiệm hẹp nằm trên tiệm may. Thứ ba chú mở cửa lúc mười hai giờ trưa, còn các ngày khác chú mở lúc chín giờ. Chiếc đồng hồ chú thích nhất làm bằng đồng, có một chú chim bạc nhỏ nhảy ra mỗi khi đến giờ. Mùa đông năm ngoái, một cậu sinh viên mang nó đến vì chim đã thôi hót. Chú Tâm tìm thấy một chiếc lò xo cong nhỏ hơn sợi tóc và sửa xong trong bốn mươi phút. Cậu sinh viên trả công bằng một chiếc khăn len tự đan, chú vẫn quàng mỗi khi cửa tiệm lạnh.",
            questions = listOf(
                CalibrationQuestion("Thứ ba chú Tâm mở cửa lúc mấy giờ?", listOf("Chín giờ", "Mười hai giờ trưa", "Sáu giờ", "Ba giờ"), 1),
                CalibrationQuestion("Cậu sinh viên đã trả công cho chú Tâm bằng gì?", listOf("Một chiếc khăn len", "Một quyển sách", "Một đồng xu bạc", "Một chú chim"), 0),
            ),
        ),
        CalibrationPassage(
            text = "Một nhóm bảy người leo núi xuất phát từ Đồi Thông lúc rạng sáng để vượt qua con đèo phía bắc. Giờ đầu tiên rất dễ đi, họ men theo con đường rộng cạnh một dòng suối yên tĩnh. Sau đó đường dốc lên và gió trở nên lạnh hơn. Khoảng giữa trưa họ dừng ở một túp lều đá và chia nhau nồi canh đậu lăng. Không ai muốn rời đi, nhưng mây đang kéo đến từ phía tây. Họ đến được bên kia lúc năm giờ, mệt nhưng vui, và đếm đủ từng người hai lần trước khi dựng lều bên một hồ nhỏ.",
            questions = listOf(
                CalibrationQuestion("Nhóm leo núi có bao nhiêu người?", listOf("Năm người", "Sáu người", "Bảy người", "Chín người"), 2),
                CalibrationQuestion("Nhóm đã ăn gì ở túp lều đá?", listOf("Canh đậu lăng", "Bánh mì với phô mai", "Cơm", "Quả mọng tươi"), 0),
            ),
        ),
    )
}
