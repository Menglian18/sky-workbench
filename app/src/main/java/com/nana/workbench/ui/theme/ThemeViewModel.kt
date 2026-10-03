package com.nana.workbench.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nana.workbench.App
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 当前外观状态。 */
data class ThemeUiState(
    val palette: NanaPalette = NanaPalettes.default,
    val fontScale: FontScale = FontScale.default,
    val followSystemDark: Boolean = false,
)

/**
 * 主题与字体的状态。选择结果存在手机本地（DataStore），所以杀掉 App 重开还是上次那套。
 */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = App.from(application).settings

    val state: StateFlow<ThemeUiState> = combine(
        settings.themeId,
        settings.fontScaleId,
        settings.followSystemDark,
    ) { themeId, fontScaleId, follow ->
        ThemeUiState(
            palette = NanaPalettes.byId(themeId),
            fontScale = FontScale.byId(fontScaleId),
            followSystemDark = follow,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeUiState())

    fun selectTheme(id: String) = viewModelScope.launch { settings.setThemeId(id) }

    fun selectFontScale(id: String) = viewModelScope.launch { settings.setFontScaleId(id) }

    fun setFollowSystemDark(enabled: Boolean) = viewModelScope.launch {
        settings.setFollowSystemDark(enabled)
    }
}
