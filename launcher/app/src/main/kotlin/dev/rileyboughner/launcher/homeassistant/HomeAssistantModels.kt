package dev.rileyboughner.launcher.homeassistant

enum class Presence { HOME, AWAY, UNKNOWN }

data class HomeAssistantLight(
    val entityId: String,
    val friendlyName: String,
    val isOn: Boolean,
    /** 0..100, or null if this light doesn't report/support brightness. */
    val brightnessPercent: Int?,
)

data class HomeAssistantScene(
    val entityId: String,
    val friendlyName: String,
)

data class HomeAssistantSnapshot(
    val presence: Presence,
    val lights: List<HomeAssistantLight>,
    val scenes: List<HomeAssistantScene>,
)
