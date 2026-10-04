package vn.homepanel.auth

import org.json.JSONObject

/** Never put these values in UI state, logs, URLs, or exception messages. */
data class HaCredentials(val accessToken: String, val refreshToken: String? = null, val clientId: String? = null, val refreshAt: Long = Long.MAX_VALUE) {
    val renewable get() = refreshToken != null && clientId != null
    fun needsRefresh(now: Long = System.currentTimeMillis()) = renewable && now >= refreshAt
    fun toJson(): String = JSONObject().put("access",accessToken).put("refresh",refreshToken).put("client",clientId).put("refreshAt",refreshAt).toString()
    override fun toString() = "HaCredentials(renewable=$renewable, secrets=redacted)"
    companion object {
        fun fromJson(raw: String): HaCredentials {
            val j=JSONObject(raw)
            return HaCredentials(j.getString("access"),j.optString("refresh").takeUnless { it.isBlank() || it=="null" },j.optString("client").takeUnless { it.isBlank() || it=="null" },j.getLong("refreshAt")).also {
                require(it.accessToken.isNotBlank() && (it.refreshToken==null)==(it.clientId==null))
            }
        }
    }
}

enum class AuthError { NETWORK, EXPIRED, BAD_RESPONSE, CANCELLED, TIMEOUT, BROWSER }
class AuthFailure(val reason: AuthError) : Exception("Home Assistant authentication: ${reason.name}")
