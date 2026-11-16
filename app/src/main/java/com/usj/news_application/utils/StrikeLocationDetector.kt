package com.usj.news_application.utils

object StrikeLocationDetector {

    private val strikeKeywords = listOf(
        "airstrike", "air strike", "drone strike", "strike on", "strikes on",
        "struck", "targeted", "targets", "bomb", "bombed", "bombing", "bombardment",
        "shelling", "shelled", "artillery", "missile", "rocket", "raid", "raids",
        "warplane", "explosion", "assassination",
        "غارة", "غارات", "قصف", "مسيرة", "مسيّرة", "استهداف", "استهدفت", "استهدف",
        "صاروخ", "صواريخ", "انفجار", "مدفعي", "حربي", "قنابل", "اغتيال"
    )

    private val nonSecurityExclusions = listOf(
        "graduation", "university", "faculty", "election", "student", "basketball", "football",
        "world bank", "imf", "electricity project", "post-withdrawal", "lebanese university",
        "تخريج", "الجامعة", "طالب", "انتخابات", "كرة السلة", "الكهرباء"
    )

    private data class LocationEntry(
        val keywords: List<String>,
        val coordinates: String
    )

    private val lebanonLocations = listOf(
        LocationEntry(listOf("janta", "yahfoufa", "nabi sheet", "جنتا", "يحفوفا", "النبي شيت"), "33.9078,36.1153"),
        LocationEntry(listOf("dahieh", "southern suburbs", "haret hreik", "ghobeiry", "choueifat", "الضاحية", "حارة حريك", "الغبيري", "الشويفات"), "33.8404,35.5031"),
        LocationEntry(listOf("tyre", "sour", "abbasiyeh", "qana", "صور", "العباسية", "قانا"), "33.2705,35.1963"),
        LocationEntry(listOf("nabatieh", "mayfadoun", "kfar sir", "zebdine", "harouf", "النبطية", "ميفدون", "زبدين", "حاروف", "كفررمان"), "33.3770,35.4838"),
        LocationEntry(listOf("sidon", "saida", "ghazieh", "zahrani", "ein el-hilweh", "صيدا", "الغازية", "الزهراني", "عين الحلوة"), "33.5571,35.3729"),
        LocationEntry(listOf("baalbek", "douris", "iyaat", "brital", "taraya", "chmistar", "بعلبك", "دورس", "إيعات", "بريتال", "طاريا", "شمسطار"), "34.0058,36.2047"),
        LocationEntry(listOf("bekaa", "zahle", "chtoura", "rayak", "البقاع", "زحلة", "شتورا", "رياق"), "33.8463,35.9020"),
        LocationEntry(listOf("hermel", "qasr", "الهرمل", "القصر"), "34.3942,36.3842"),
        LocationEntry(listOf("khiam", "el khiam", "الخيام"), "33.3323,35.5902"),
        LocationEntry(listOf("marjayoun", "odaisseh", "kfar kila", "deir mimas", "مرجعيون", "العديسة", "كفركلا", "دير ميماس"), "33.3592,35.5898"),
        LocationEntry(listOf("bint jbeil", "aita al-shaab", "ayta ash shab", "maroun al-ras", "yaroun", "tebnine", "mais al-jabal", "بنت جبيل", "عيتا الشعب", "مارون الراس", "يارون", "تبنين", "ميس الجبل", "عيناثا"), "33.1214,35.4389"),
        LocationEntry(listOf("naqoura", "alma al-shaab", "dhayra", "tayr harfa", "labbouneh", "الناقورة", "علما الشعب", "الضهيرة", "طيرحرفا", "اللبونة"), "33.1189,35.1389"),
        LocationEntry(listOf("shebaa", "kfarshouba", "hasbaya", "arkoub", "شبعا", "كفرشوبا", "حاصبيا", "العرقوب"), "33.3386,35.7369"),
        LocationEntry(listOf("jezzine", "rihane", "جزين", "الريحان"), "33.5417,35.5844"),
        LocationEntry(listOf("damour", "jiyeh", "الدامور", "الجية"), "33.7333,35.4500"),
        LocationEntry(listOf("beirut", "achrafieh", "ras el nabaa", "cola", "bachoura", "بيروت", "الأشرفية", "رأس النبع", "الكولا", "الباشورة"), "33.8938,35.5018"),
        LocationEntry(listOf("jounieh", "jbeil", "byblos", "keserwan", "جونيه", "جونية", "جبيل", "كسروان"), "33.9808,35.6178"),
        LocationEntry(listOf("batroun", "البترون"), "34.2553,35.6581"),
        LocationEntry(listOf("tripoli", "beddawi", "akkar", "طرابلس", "البداوي", "عكار"), "34.4346,35.8362"),
        LocationEntry(listOf("south lebanon", "southern lebanon", "الجنوب", "جنوب لبنان"), "33.2721,35.3960")
    )

    fun detectStrikeCoordinates(title: String, description: String, detailedText: String = ""): String {
        val primaryText = "$title $description".lowercase()
        val fullText = "$title $description $detailedText".lowercase()

        if (nonSecurityExclusions.any { primaryText.contains(it) }) {
            return ""
        }

        val isStrikeEvent = strikeKeywords.any { primaryText.contains(it) }
        if (!isStrikeEvent) return ""

        for (entry in lebanonLocations) {
            if (entry.keywords.any { primaryText.contains(it) }) {
                return entry.coordinates
            }
        }
        for (entry in lebanonLocations) {
            if (entry.keywords.any { fullText.contains(it) }) {
                return entry.coordinates
            }
        }

        return ""
    }
}
