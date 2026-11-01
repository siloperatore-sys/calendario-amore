package com.calendarietto.app.data

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Le poche chiamate a Google Calendar che il telefono da solo non sa fare:
 * creare un calendario e condividerlo con un'email.
 * Tutto il resto (leggere, aggiungere, sincronizzare) passa dal calendario di Android.
 */
object GoogleCalendarApi {

    const val SCOPE = "https://www.googleapis.com/auth/calendar"
    private const val BASE = "https://www.googleapis.com/calendar/v3"

    class ApiError(val code: Int, val reason: String, message: String) : Exception(message)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun call(token: String, method: String, path: String, body: JSONObject? = null): JSONObject {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 20_000
            c.readTimeout = 20_000
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Accept", "application/json")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                var reason = ""
                var msg = "Errore $code"
                try {
                    val err = JSONObject(text).getJSONObject("error")
                    msg = err.optString("message", msg)
                    reason = err.optJSONArray("errors")?.optJSONObject(0)?.optString("reason", "") ?: ""
                    if (reason.isBlank()) reason = err.optString("status", "")
                } catch (_: Exception) {
                }
                throw ApiError(code, reason, msg)
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            c.disconnect()
        }
    }

    /** Cerca tra i calendari di cui sei proprietario uno con questo nome. */
    fun findOwnedCalendar(token: String, name: String): String? {
        val res = call(token, "GET", "/users/me/calendarList?minAccessRole=owner&maxResults=250")
        val items = res.optJSONArray("items") ?: return null
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            if (it.optString("summary").trim() == name.trim()) return it.getString("id")
        }
        return null
    }

    fun createCalendar(token: String, name: String, timeZone: String): String {
        val body = JSONObject()
            .put("summary", name)
            .put("description", "Il calendario condiviso di Calendarietto 🎀")
            .put("timeZone", timeZone)
        return call(token, "POST", "/calendars", body).getString("id")
    }

    /** Condivide il calendario con un'email. role: "writer" (può modificare) o "reader" (solo vedere). */
    fun share(token: String, calendarId: String, email: String, role: String) {
        val body = JSONObject()
            .put("role", role)
            .put("scope", JSONObject().put("type", "user").put("value", email.trim()))
        try {
            call(token, "POST", "/calendars/${enc(calendarId)}/acl?sendNotifications=true", body)
        } catch (e: ApiError) {
            if (e.code != 409) throw e // 409 = già condiviso con questa persona: va bene così
        }
    }

    /** Link che, aperto da chi è stato invitato, aggiunge il calendario al suo Google Calendar. */
    fun addCalendarLink(calendarId: String): String {
        val b64 = Base64.encodeToString(calendarId.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.NO_PADDING)
        return "https://calendar.google.com/calendar/u/0/r?cid=" + enc(b64)
    }
}
