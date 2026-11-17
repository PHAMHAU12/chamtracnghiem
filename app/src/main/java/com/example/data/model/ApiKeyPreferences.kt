package com.example.data.model

import android.content.Context
import android.content.SharedPreferences

object ApiKeyPreferences {
    private const val PREFS_NAME = "gemini_ai_prefs"
    private const val KEY_CUSTOM_API_KEY = "custom_gemini_api_key"
    private const val KEY_SELECTED_MODEL = "selected_gemini_model"
    private const val KEY_ENABLE_FALLBACK = "enable_model_fallback"

    val AVAILABLE_MODELS = listOf(
        ModelOption(
            id = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash (Khuyên dùng)",
            description = "Nhanh nhất, tối ưu cho nhận diện thị giác OMR và chấm điểm tốc độ cao",
            isDefault = true
        ),
        ModelOption(
            id = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            description = "Dự phòng 1: Ổn định, độ chính xác cao cho văn bản và bảng biểu",
            isDefault = false
        ),
        ModelOption(
            id = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro",
            description = "Dự phòng 2: Phân tích sâu, xử lý các phiếu mờ, nghiêng hoặc tẩy xóa phức tạp",
            isDefault = false
        )
    )

    data class ModelOption(
        val id: String,
        val displayName: String,
        val description: String,
        val isDefault: Boolean
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getCustomApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_API_KEY, "")?.trim().orEmpty()
    }

    fun setCustomApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_API_KEY, key.trim()).apply()
    }

    fun getSelectedModel(context: Context): String {
        return getPrefs(context).getString(KEY_SELECTED_MODEL, "gemini-3.8-flash") ?: "gemini-3.8-flash"
    }

    fun setSelectedModel(context: Context, model: String) {
        getPrefs(context).edit().putString(KEY_SELECTED_MODEL, model).apply()
    }

    fun isFallbackEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLE_FALLBACK, true)
    }

    fun setFallbackEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLE_FALLBACK, enabled).apply()
    }

    fun getModelFallbackChain(context: Context): List<String> {
        val selected = getSelectedModel(context)
        val all = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-3.8-flash", "gemini-2.5-pro")
        return listOf(selected) + all.filter { it != selected }
    }
}
