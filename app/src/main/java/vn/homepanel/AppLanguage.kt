package vn.homepanel

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Explicit app preference; English is also the resource fallback. */
object AppLanguage {
    private val mutable = MutableStateFlow("en")
    val language = mutable.asStateFlow()
    fun load(context: Context) { mutable.value = context.getSharedPreferences("language",0).getString("tag","en").let { if (it == "vi") "vi" else "en" } }
    fun select(context: Context, tag: String) {
        require(tag in setOf("en","vi"))
        context.getSharedPreferences("language",0).edit().putString("tag",tag).apply()
        mutable.value = tag
    }
    fun context(base: Context, tag: String = mutable.value): Context = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(tag))) })
    fun text(base: Context, id: Int, vararg args: Any): String = context(base).getString(id,*args)
}

@Composable fun LocalizedContent(content: @Composable () -> Unit) {
    val tag by AppLanguage.language.collectAsState()
    val base = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(base,tag,configuration) { AppLanguage.context(base,tag) }
    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration, content = content)
}
