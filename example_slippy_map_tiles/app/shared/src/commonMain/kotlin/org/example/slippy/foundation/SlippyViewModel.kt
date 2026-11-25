package org.example.slippy.foundation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Screen { Tile, Account }

/** The app's view model: it owns the container, and knows the active screen. */
class SlippyViewModel : ViewModel() {
    val appContainer = AppContainer(viewModelScope)

    val activeScreen: StateFlow<Screen>
        field = MutableStateFlow(Screen.Tile)

    fun setActiveScreen(screen: Screen) {
        activeScreen.value = screen
    }
}
