package vn.homepanel

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The free sideload edition does not require a Meta purchase or the Platform SDK. */
class EntitlementGate(@Suppress("UNUSED_PARAMETER") context: Context) {
    val flow = MutableStateFlow(Access.GRANTED).asStateFlow()
    val failureCode = MutableStateFlow<String?>(null).asStateFlow()
    val allowed get() = true
    fun check() = Unit
}
