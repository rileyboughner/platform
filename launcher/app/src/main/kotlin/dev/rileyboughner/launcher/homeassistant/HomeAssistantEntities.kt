package dev.rileyboughner.launcher.homeassistant

/**
 * Single source of truth for which Home Assistant entities the Home
 * Assistant page shows — the same entity IDs already wired up in the
 * Quickshell side panel (dotfiles). Edit here to add/remove a light or
 * scene row.
 */
object HomeAssistantEntities {

    const val PRESENCE_ENTITY_ID: String = "person.rileyboughner"

    val LIGHT_ENTITY_IDS: List<String> = listOf(
        "light.main_lights",
        "light.living_room_lamp",
        "light.living_room_floor_lamp",
        "light.bedroom_main_light_1",
        "light.desk_light",
        "light.salt_lamp_plug",
    )

    val SCENE_ENTITY_IDS: List<String> = listOf(
        "scene.chill_vibes",
        "scene.living_room_normal",
        "scene.living_room_movie",
        "scene.living_room_bright",
        "scene.living_room_red_moody",
        "scene.living_room_early_morning",
        "scene.bedroom_normal",
        "scene.bedroom_relax",
        "scene.bedroom_sleep",
        "scene.bedroom_morning",
        "scene.bedroom_reading",
        "scene.bedroom_night_red",
        "scene.bedroom_wake_up",
        "scene.bedroom_cozy",
    )
}
