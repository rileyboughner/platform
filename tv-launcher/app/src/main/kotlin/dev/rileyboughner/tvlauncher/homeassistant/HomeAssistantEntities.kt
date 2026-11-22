package dev.rileyboughner.tvlauncher.homeassistant

/**
 * Single source of truth for which Home Assistant entities the TV home
 * screen shows. This launcher is living-room only: its lights and scenes
 * rows cover the Living Room, the room the TV lives in. Edit here to
 * add/remove a light or scene.
 */
object HomeAssistantEntities {

    const val PRESENCE_ENTITY_ID: String = "person.rileyboughner"

    data class RoomDefinition(
        val name: String,
        val lightEntityIds: List<String>,
        val sceneEntityIds: List<String>,
    )

    val ROOMS: List<RoomDefinition> = listOf(
        RoomDefinition(
            name = "Living Room",
            lightEntityIds = listOf(
                "light.main_lights",
                "light.living_room_lamp",
                "light.living_room_floor_lamp",
            ),
            sceneEntityIds = listOf(
                "scene.chill_vibes",
                "scene.living_room_normal",
                "scene.living_room_movie",
                "scene.living_room_bright",
                "scene.living_room_red_moody",
                "scene.living_room_early_morning",
            ),
        ),
    )
}
