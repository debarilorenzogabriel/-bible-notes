package it.lgdb.biblenotes.domain

import it.lgdb.biblenotes.model.AppLanguage

enum class BibleTestament {
    HEBREW_ARAMAIC,
    CHRISTIAN_GREEK
}

data class BibleBook(
    val number: Int,
    val nameIt: String,
    val abbrIt: String,
    val nameEn: String,
    val abbrEn: String,
    val chapters: Int,
    val testament: BibleTestament
) {
    fun localizedName(language: AppLanguage): String =
        if (language == AppLanguage.EN) nameEn else nameIt

    fun localizedAbbr(language: AppLanguage): String =
        if (language == AppLanguage.EN) abbrEn else abbrIt
}

object BibleCatalog {
    val books: List<BibleBook> = listOf(
        // Scritture Ebraico-Aramaiche (1-39)
        BibleBook(1, "Genesi", "Gen", "Genesis", "Gen", 50, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(2, "Esodo", "Eso", "Exodus", "Ex", 40, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(3, "Levitico", "Lev", "Leviticus", "Lev", 27, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(4, "Numeri", "Num", "Numbers", "Num", 36, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(5, "Deuteronomio", "Deut", "Deuteronomy", "Deut", 34, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(6, "Giosuè", "Gios", "Joshua", "Josh", 24, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(7, "Giudici", "Giud", "Judges", "Judg", 21, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(8, "Rut", "Rut", "Ruth", "Ruth", 4, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(9, "1 Samuele", "1Sa", "1 Samuel", "1Sa", 31, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(10, "2 Samuele", "2Sa", "2 Samuel", "2Sa", 24, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(11, "1 Re", "1Re", "1 Kings", "1Ki", 22, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(12, "2 Re", "2Re", "2 Kings", "2Ki", 25, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(13, "1 Cronache", "1Cr", "1 Chronicles", "1Ch", 29, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(14, "2 Cronache", "2Cr", "2 Chronicles", "2Ch", 36, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(15, "Esdra", "Esd", "Ezra", "Ezr", 10, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(16, "Neemia", "Nee", "Nehemiah", "Neh", 13, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(17, "Ester", "Est", "Esther", "Est", 10, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(18, "Giobbe", "Giob", "Job", "Job", 42, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(19, "Salmi", "Sal", "Psalms", "Ps", 150, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(20, "Proverbi", "Prov", "Proverbs", "Pr", 31, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(21, "Ecclesiaste", "Eccl", "Ecclesiastes", "Ec", 12, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(22, "Cantico dei Cantici", "Cant", "Song of Solomon", "Ca", 8, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(23, "Isaia", "Isa", "Isaiah", "Isa", 66, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(24, "Geremia", "Ger", "Jeremiah", "Jer", 52, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(25, "Lamentazioni", "Lam", "Lamentations", "La", 5, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(26, "Ezechiele", "Ezec", "Ezekiel", "Eze", 48, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(27, "Daniele", "Dan", "Daniel", "Da", 12, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(28, "Osea", "Osea", "Hosea", "Ho", 14, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(29, "Gioele", "Gioe", "Joel", "Joe", 3, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(30, "Amos", "Amos", "Amos", "Am", 9, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(31, "Abdia", "Abd", "Obadiah", "Ob", 1, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(32, "Giona", "Gion", "Jonah", "Jon", 4, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(33, "Michea", "Mich", "Micah", "Mic", 7, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(34, "Naum", "Naum", "Nahum", "Na", 3, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(35, "Abacuc", "Abac", "Habakkuk", "Hab", 3, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(36, "Sofonia", "Sof", "Zephaniah", "Zep", 3, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(37, "Aggeo", "Agg", "Haggai", "Hag", 2, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(38, "Zaccaria", "Zacc", "Zechariah", "Zec", 14, BibleTestament.HEBREW_ARAMAIC),
        BibleBook(39, "Malachia", "Mal", "Malachi", "Mal", 4, BibleTestament.HEBREW_ARAMAIC),

        // Scritture Greche-Cristiane (40-66)
        BibleBook(40, "Matteo", "Matt", "Matthew", "Mt", 28, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(41, "Marco", "Mar", "Mark", "Mr", 16, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(42, "Luca", "Luca", "Luke", "Lu", 24, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(43, "Giovanni", "Giov", "John", "Joh", 21, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(44, "Atti", "Atti", "Acts", "Ac", 28, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(45, "Romani", "Rom", "Romans", "Ro", 16, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(46, "1 Corinti", "1Cor", "1 Corinthians", "1Co", 16, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(47, "2 Corinti", "2Cor", "2 Corinthians", "2Co", 13, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(48, "Galati", "Gal", "Galatians", "Ga", 6, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(49, "Efesini", "Efes", "Ephesians", "Eph", 6, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(50, "Filippesi", "Filip", "Philippians", "Php", 4, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(51, "Colossesi", "Col", "Colossians", "Col", 4, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(52, "1 Tessalonicesi", "1Tess", "1 Thessalonians", "1Th", 5, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(53, "2 Tessalonicesi", "2Tess", "2 Thessalonians", "2Th", 3, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(54, "1 Timoteo", "1Tim", "1 Timothy", "1Ti", 6, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(55, "2 Timoteo", "2Tim", "2 Timothy", "2Ti", 4, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(56, "Tito", "Tito", "Titus", "Tit", 3, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(57, "Filemone", "Filem", "Philemon", "Phm", 1, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(58, "Ebrei", "Ebr", "Hebrews", "Heb", 13, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(59, "Giacomo", "Giac", "James", "Jas", 5, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(60, "1 Pietro", "1Pie", "1 Peter", "1Pe", 5, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(61, "2 Pietro", "2Pie", "2 Peter", "2Pe", 3, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(62, "1 Giovanni", "1Gio", "1 John", "1Jo", 5, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(63, "2 Giovanni", "2Gio", "2 John", "2Jo", 1, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(64, "3 Giovanni", "3Gio", "3 John", "3Jo", 1, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(65, "Giuda", "Giuda", "Jude", "Jude", 1, BibleTestament.CHRISTIAN_GREEK),
        BibleBook(66, "Rivelazione", "Riv", "Revelation", "Re", 22, BibleTestament.CHRISTIAN_GREEK)
    )

    val hebrewBooks: List<BibleBook> = books.filter { it.testament == BibleTestament.HEBREW_ARAMAIC }
    val greekBooks: List<BibleBook> = books.filter { it.testament == BibleTestament.CHRISTIAN_GREEK }

    fun findByName(name: String): BibleBook? {
        val normalized = name.trim()
        return books.firstOrNull {
            it.nameIt.equals(normalized, ignoreCase = true) ||
                it.nameEn.equals(normalized, ignoreCase = true)
        }
    }

    fun findByNumber(number: Int): BibleBook? = books.firstOrNull { it.number == number }
}
