package asagiribeta.serverMarket.util

object Language {
    private const val DEFAULT_LANGUAGE = "en"
    private var currentLanguage: String = DEFAULT_LANGUAGE

    fun getCurrentLanguage(): String = currentLanguage

    fun setLanguage(lang: String): Boolean {
        currentLanguage = if (lang.isBlank()) DEFAULT_LANGUAGE else lang
        return true
    }
}
