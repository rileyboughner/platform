package dev.rileyboughner.launcher

/**
 * Single hardcoded source of truth for the homelab dashboard's service
 * links. Edit here to add/remove/reorder a row — order here is the order
 * shown on the dashboard page.
 */
object HomelabLinks {

    data class Link(val label: String, val url: String)

    val LINKS: List<Link> = listOf(
        Link("Media", "https://media.rileyboughner.dev"),
        Link("Requests", "https://requests.rileyboughner.dev"),
        Link("Pictures", "https://pictures.rileyboughner.dev"),
        Link("Home", "https://home.rileyboughner.dev"),
        Link("Grafana", "https://grafana.rileyboughner.dev"),
        Link("Auth", "https://auth.rileyboughner.dev"),
        Link("Cameras", "https://cameras.rileyboughner.dev"),
        Link("Movies", "https://movies.rileyboughner.dev"),
    )
}
