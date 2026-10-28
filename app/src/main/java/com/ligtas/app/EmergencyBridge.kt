package com.ligtas.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModel

/**
 * Holds which emergency type is highlighted. Compose redraws whenever [selected] changes, and because it lives in a
 * ViewModel it also survives screen rotation. The real selection logic (templates, edits) stays in MainActivity.java.
 */
class EmergencyViewModel : ViewModel() {
    var selected by mutableIntStateOf(-1)
        private set

    /** -1 clears the highlight. Called from Java. */
    fun select(index: Int) {
        selected = index
    }

    /** Whether the admin's top-left menu is open. */
    var menuOpen by mutableStateOf(false)

    /** The Status pill is faded and untappable until an alert has been sent. */
    var statusEnabled by mutableStateOf(false)
        private set

    /** The Edit pill is faded until an emergency type is picked; it reads "Edited" after changes were saved. */
    var editEnabled by mutableStateOf(false)
        private set
    var edited by mutableStateOf(false)
        private set

    /** "Hi, Aeron". Empty until Java sets it (the header then shows the default greeting). */
    var greeting by mutableStateOf("")
        private set

    fun updateGreeting(text: String) {
        greeting = text
    }

    fun updateStatusEnabled(enabled: Boolean) {
        statusEnabled = enabled
    }

    fun updateEdit(enabled: Boolean, edited: Boolean) {
        editEnabled = enabled
        this.edited = edited
    }

    // ---- slide-to-send confirmation card ----
    var confirmVisible by mutableStateOf(false)
        private set

    /** Increases every time the card opens, so the slider always starts from the beginning. */
    var confirmSession by mutableIntStateOf(0)
        private set
    var confirmTitle by mutableStateOf("")
        private set
    var confirmPreview by mutableStateOf("")
        private set
    var confirmIcon by mutableIntStateOf(R.drawable.ic_siren)
        private set

    fun showConfirm(title: String) {
        confirmTitle = title
        confirmPreview = ""
        confirmSession += 1
        confirmVisible = true
    }

    fun updateConfirmPreview(text: String) {
        confirmPreview = text
    }

    fun updateConfirmIcon(res: Int) {
        confirmIcon = res
    }

    fun hideConfirm() {
        confirmVisible = false
    }
}

/** What the Compose screen reports back to the Java activity. */
interface EmergencyListener {
    fun onTypeSelected(index: Int)
    fun onAlertEveryone()
    fun onEdit()
    fun onStatus()
    fun onProfile()
    fun onConfirmSend()
    fun onConfirmCancel()
    fun onReadiness()
    fun onSuspend()
    fun onConsole()
}

object EmergencyBridge {
    /** Puts the Compose emergency screen inside a ComposeView that sits in the old XML layout. */
    @JvmStatic
    fun attach(view: ComposeView, viewModel: EmergencyViewModel, listener: EmergencyListener) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            LigtasTheme {
                EmergencyPanel(
                    selected = viewModel.selected,
                    onSelect = { listener.onTypeSelected(it) },
                    onAlertEveryone = { listener.onAlertEveryone() },
                    statusEnabled = viewModel.statusEnabled,
                    editEnabled = viewModel.editEnabled,
                    edited = viewModel.edited,
                    onStatus = { listener.onStatus() },
                    onEdit = { listener.onEdit() },
                )
            }
        }
    }
}

object ConfirmBridge {
    /** Puts the slide-to-send card inside the full-screen ComposeView of the dashboard. */
    @JvmStatic
    fun attach(view: ComposeView, viewModel: EmergencyViewModel, listener: EmergencyListener) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            LigtasTheme {
                if (viewModel.confirmVisible) {
                    ConfirmAlertOverlay(
                        session = viewModel.confirmSession,
                        iconRes = viewModel.confirmIcon,
                        title = viewModel.confirmTitle,
                        preview = viewModel.confirmPreview,
                        onConfirmed = { listener.onConfirmSend() },
                        onCancel = { listener.onConfirmCancel() },
                    )
                }
            }
        }
    }
}
