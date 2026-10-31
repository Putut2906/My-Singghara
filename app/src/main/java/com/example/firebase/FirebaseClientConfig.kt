package com.example.firebase

import android.content.Context
import org.json.JSONObject

/**
 * Firebase client settings taken from the bundled google-services.json so the App ID,
 * API key and web client always follow the file shipped in app/src/main/assets.
 */
data class FirebaseClientConfig(
    val appId: String,
    val apiKey: String,
    val projectId: String,
    val storageBucket: String,
    val webClientId: String,
    // Package/certificate the bundled google-services.json was registered for.
    val registeredPackage: String = "",
    val registeredCertHash: String = ""
) {
    companion object {
        private const val ASSET_NAME = "google-services.json"

        // Only used when the asset is missing or unreadable.
        private val fallback = FirebaseClientConfig(
            appId = "1:194078759561:android:b39d13cb8202ff43cfc458",
            apiKey = "AIzaSyC-1wqeoghlBosva2liyNCEEbb4IK6yvYs",
            projectId = "mobile-d08aa",
            storageBucket = "mobile-d08aa.firebasestorage.app",
            webClientId = "194078759561-1b27n44qm8r8r8j3bhj032d50faq4109.apps.googleusercontent.com"
        )

        @Volatile
        private var cached: FirebaseClientConfig? = null

        fun load(context: Context): FirebaseClientConfig {
            cached?.let { return it }
            val resolved = runCatching { parse(context.applicationContext) }.getOrNull() ?: fallback
            cached = resolved
            return resolved
        }

        private fun parse(context: Context): FirebaseClientConfig? {
            val root = JSONObject(
                context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            )
            val projectInfo = root.optJSONObject("project_info")
            val clients = root.optJSONArray("client") ?: return null
            if (clients.length() == 0) return null

            // Prefer the client registered for this package; older files only carry another one.
            var matched: JSONObject? = null
            var first: JSONObject? = null
            for (i in 0 until clients.length()) {
                val client = clients.optJSONObject(i) ?: continue
                if (first == null) first = client
                val packageName = client.optJSONObject("client_info")
                    ?.optJSONObject("android_client_info")
                    ?.optString("package_name")
                if (packageName == context.packageName) {
                    matched = client
                    break
                }
            }
            val client = matched ?: first ?: return null

            val appId = client.optJSONObject("client_info")?.optString("mobilesdk_app_id").orEmpty()
            val registeredPackage = client.optJSONObject("client_info")
                ?.optJSONObject("android_client_info")
                ?.optString("package_name")
                .orEmpty()
            val apiKey = client.optJSONArray("api_key")?.optJSONObject(0)?.optString("current_key").orEmpty()
            if (appId.isBlank() || apiKey.isBlank()) return null

            var webClientId = ""
            var certHash = ""
            val oauthClients = client.optJSONArray("oauth_client")
            if (oauthClients != null) {
                for (i in 0 until oauthClients.length()) {
                    val oauth = oauthClients.optJSONObject(i) ?: continue
                    if (oauth.optInt("client_type") == 3 && webClientId.isBlank()) {
                        webClientId = oauth.optString("client_id")
                    }
                    if (oauth.optInt("client_type") == 1 && certHash.isBlank()) {
                        certHash = oauth.optJSONObject("android_info")
                            ?.optString("certificate_hash")
                            .orEmpty()
                    }
                }
            }

            return FirebaseClientConfig(
                appId = appId,
                apiKey = apiKey,
                projectId = projectInfo?.optString("project_id").orEmpty(),
                storageBucket = projectInfo?.optString("storage_bucket").orEmpty(),
                webClientId = webClientId.ifBlank { fallback.webClientId },
                registeredPackage = registeredPackage,
                registeredCertHash = certHash
            )
        }
    }
}
