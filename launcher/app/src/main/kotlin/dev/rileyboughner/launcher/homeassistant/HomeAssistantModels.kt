package dev.rileyboughner.launcher.homeassistant

enum class Presence { HOME, AWAY, UNKNOWN }

data class HomeAssistantLight(
    val entityId: String,
    val friendlyName: String,
    val isOn: Boolean,
    /** 0..100, or null if this light doesn't report/support brightness. */
    val brightnessPercent: Int?,
    /** False when the raw HA state is "unavailable"/"unknown" (e.g. a Zigbee device that never rejoined). */
    val isAvailable: Boolean = true,
)

data class HomeAssistantScene(
    val entityId: String,
    val friendlyName: String,
)

data class HomeAssistantRoom(
    val name: String,
    val lights: List<HomeAssistantLight>,
    val scenes: List<HomeAssistantScene>,
)

data class HomeAssistantSnapshot(
    val presence: Presence,
    val rooms: List<HomeAssistantRoom>,
)
