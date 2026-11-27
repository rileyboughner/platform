import Quickshell
import Quickshell.Wayland
import Quickshell.Io
import Quickshell.Hyprland
import Quickshell.Services.Pipewire
import Quickshell.Networking
import Quickshell.Bluetooth
import Quickshell.Widgets
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls

PanelWindow {
    id: root

    // ─────────────────────────────────────────────
    // Theme
    // ─────────────────────────────────────────────


property color colBg: "#101b27"
property color colFg: "#c3c6c9"
property color colMuted: "#5f6975"
property color colAccent: "#A4595B"
property color colRed: "#e06c75"

property var walColors: ({})

FileView {
    id: walColorsFile

    path: Quickshell.env("HOME") + "/.cache/wal/colors.json"

    watchChanges: true

    onLoaded: {
        try {
            const parsed = JSON.parse(text())

            root.walColors = parsed

            root.colBg = parsed.special.background
            root.colFg = parsed.special.foreground
            root.colMuted = parsed.colors.color8
            root.colAccent = parsed.colors.color4
            root.colRed = parsed.colors.color1

            console.log("Pywal colors loaded:")
            console.log("Background:", root.colBg)
            console.log("Foreground:", root.colFg)
            console.log("Muted:", root.colMuted)
            console.log("Accent:", root.colAccent)
            console.log("Red:", root.colRed)

        } catch (error) {
            console.log("Failed to load Pywal colors:", error)
        }
    }
}   

property string fontFamily: "JetBrainsMono Nerd Font"
    property int fontSize: 14

    // ─────────────────────────────────────────────
    // System data
    // ─────────────────────────────────────────────

    readonly property var connectedWifiNetwork: {
        const device = Networking.devices.values.find((d) => d.type === DeviceType.Wifi)
        if (!device) return null
        return device.networks.values.find((n) => n.connected) || null
    }
    property string ipAddress: "Unknown"
    property int volumePercent: 0
    property int batteryPercent: 0
    property bool batteryCharging: false
    property bool hasBattery: false
    property string focusedWindowTitle: "Desktop"
    property string weatherTemp: ""
    property string weatherCondition: ""
    property string weatherIcon: "󰖕"
    property bool hasNextEvent: false
    property bool calendarAuthenticated: false
    property string nextEventTitle: ""
    property real nextEventStartMs: 0
    property real nowMs: Date.now()
    property var todayEvents: []
    property bool hasTasksData: false
    property int tasksOverdueCount: 0
    property int tasksDueTodayCount: 0
    property bool dotfilesHaveUnstagedChanges: false

    // Display state
    // Wi-Fi scroll mode: 0 = network name, 1 = IP address, 2 = up/down speed
    property int wifiScrollMode: 0
    property real wifiDownSpeed: 0
    property real wifiUpSpeed: 0
    property real netPrevRxBytes: -1
    property real netPrevTxBytes: -1
    property real netPrevSampleTime: 0
    property bool showFullDate: false
    // Display mode: 0 = icon only, 1 = label only, 2 = icon + label
    property int weatherDisplayMode: 2
    property int calendarDisplayMode: 0
    property int wifiDisplayMode: 2
    property int volumeDisplayMode: 2
    property int bluetoothDisplayMode: 2
    property int batteryDisplayMode: 2
    property int taskDisplayMode: 2
    property bool batteryBlinkOn: true
    property var wifiPasswordTarget: null
    property bool vpnActive: false
    property var vpnConfigs: []
    property var vpnActiveInterfaces: []

    function toggleVpn(name) {
        const action = root.vpnActiveInterfaces.includes(name) ? "down" : "up"
        vpnToggleProcess.command = ["sudo", "-n", "/run/current-system/sw/bin/wg-quick", action, name]
        vpnToggleProcess.running = true
    }

    function weatherIconFor(condition) {
        const c = condition.toLowerCase()

        if (c.includes("thunder")) return "󰖓"
        if (c.includes("sleet")) return "󰙿"
        if (c.includes("blizzard") || c.includes("snow")) return "󰖘"
        if (c.includes("ice pellets") || c.includes("hail")) return "󰖒"
        if (c.includes("drizzle") || c.includes("rain")) return "󰖗"
        if (c.includes("fog") || c.includes("mist") || c.includes("haze")) return "󰖑"
        if (c.includes("partly cloudy")) return "󰖕"
        if (c.includes("overcast") || c.includes("cloud")) return "󰖐"
        if (c.includes("clear") || c.includes("sunny")) return "󰖙"

        return "󰖕"
    }

    function cycleDisplayMode(mode) {
        return (mode + 1) % 3
    }

    function wifiIconFor(net) {
        if (!net) return "󰤮"

        // signalStrength is a 0-1 fraction, not a 0-100 percentage
        const s = net.signalStrength * 100
        if (s >= 80) return "󰤨"
        if (s >= 60) return "󰤥"
        if (s >= 40) return "󰤢"
        if (s >= 20) return "󰤟"
        return "󰤯"
    }

    function formatSpeed(bytesPerSec) {
        if (bytesPerSec >= 1024 * 1024) return (bytesPerSec / (1024 * 1024)).toFixed(1) + " MB/s"
        if (bytesPerSec >= 1024) return (bytesPerSec / 1024).toFixed(0) + " KB/s"
        return Math.round(bytesPerSec) + " B/s"
    }

    function formatCountdown(ms) {
        if (ms <= 0) return "Now"

        const totalMinutes = Math.round(ms / 60000)
        const days = Math.floor(totalMinutes / 1440)
        const hours = Math.floor((totalMinutes % 1440) / 60)
        const minutes = totalMinutes % 60

        if (days > 0) return days + "d " + hours + "h"
        if (hours > 0) return hours + "h " + minutes + "m"
        return minutes + "m"
    }

    function togglePopup(target) {
        const wasVisible = target.visible

        audioPopup.visible = false
        wifiPopup.visible = false
        bluetoothPopup.visible = false
        wallpaperPopup.visible = false
        calendarPopup.visible = false
        themePopup.visible = false
        sidePanel.visible = false
        todoistPanel.visible = false
        calendarPanel.visible = false
        smsPanel.visible = false

        // A normal open (SUPER+C / SUPER+N or the bar) is never fullscreen.
        calendarPanel.fullscreen = false
        todoistPanel.fullscreen = false

        target.visible = !wasVisible
    }

    // Fullscreen toggle for the calendar/task panels (IPC targets
    // calendar-fullscreen / todoist-fullscreen, bound to SUPER+ALT+C and
    // SUPER+ALT+N). If already fullscreen, hide it; otherwise hide
    // everything else and show this panel at scratchpad size (92%).
    function togglePanelFullscreen(target) {
        if (target.visible && target.fullscreen === true) {
            target.visible = false
            target.fullscreen = false
            return
        }

        audioPopup.visible = false
        wifiPopup.visible = false
        bluetoothPopup.visible = false
        wallpaperPopup.visible = false
        calendarPopup.visible = false
        themePopup.visible = false
        sidePanel.visible = false
        todoistPanel.visible = false
        calendarPanel.visible = false
        smsPanel.visible = false

        target.fullscreen = true
        target.visible = true
    }

    anchors.top: true
    anchors.left: true
    anchors.right: true

    implicitHeight: 30
    color: root.colBg

    // ─────────────────────────────────────────────
    // IP Address
    // ─────────────────────────────────────────────

    Process {
        id: ipProcess

        command: [
            "sh",
            "-c",
            "ip -4 route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if ($i==\"src\") print $(i+1)}'"
        ]

        stdout: StdioCollector {
            onStreamFinished: {
                root.ipAddress = text.trim() || "Unknown"
            }
        }
    }

    Timer {
        interval: 5000
        running: true
        repeat: true
        triggeredOnStart: true

        onTriggered: {
            ipProcess.running = true
        }
    }

    // ─────────────────────────────────────────────
    // Network Speed
    // ─────────────────────────────────────────────

    Process {
        id: netSpeedProcess

        command: [
            "sh",
            "-c",
            "iface=$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if ($i==\"dev\") print $(i+1)}'); [ -n \"$iface\" ] && cat \"/sys/class/net/$iface/statistics/rx_bytes\" \"/sys/class/net/$iface/statistics/tx_bytes\" 2>/dev/null"
        ]

        stdout: StdioCollector {
            onStreamFinished: {
                const parts = text.trim().split(/\s+/).map(Number)
                const now = Date.now()

                if (parts.length === 2 && !isNaN(parts[0]) && !isNaN(parts[1])) {
                    if (root.netPrevSampleTime > 0) {
                        const elapsed = (now - root.netPrevSampleTime) / 1000
                        if (elapsed > 0) {
                            root.wifiDownSpeed = Math.max(0, (parts[0] - root.netPrevRxBytes) / elapsed)
                            root.wifiUpSpeed = Math.max(0, (parts[1] - root.netPrevTxBytes) / elapsed)
                        }
                    }
                    root.netPrevRxBytes = parts[0]
                    root.netPrevTxBytes = parts[1]
                    root.netPrevSampleTime = now
                } else {
                    root.wifiDownSpeed = 0
                    root.wifiUpSpeed = 0
                    root.netPrevSampleTime = 0
                }
            }
        }
    }

    Timer {
        interval: 2000
        running: true
        repeat: true
        triggeredOnStart: true

        onTriggered: {
            netSpeedProcess.running = true
        }
    }

    // ─────────────────────────────────────────────
    // Volume
    // ─────────────────────────────────────────────

    Process {
        id: volumeProcess

        command: [
            "sh",
            "-c",
            "wpctl get-volume @DEFAULT_AUDIO_SINK@ | awk '{print int($2 * 100)}'"
        ]

        stdout: StdioCollector {
            onStreamFinished: {
                root.volumePercent = parseInt(text.trim()) || 0
            }
        }
    }

    Process {
        id: volumeChangeProcess
    }

    Timer {
        interval: 1000
        running: true
        repeat: true
        triggeredOnStart: true

        onTriggered: {
            volumeProcess.running = true
        }
    }

    // ─────────────────────────────────────────────
    // Battery
    // ─────────────────────────────────────────────

    Process {
        id: batteryProcess

        command: [
            "sh",
            "-c",
            "cat /sys/class/power_supply/BAT*/capacity 2>/dev/null | head -1; cat /sys/class/power_supply/BAT*/status 2>/dev/null | head -1"
        ]

        stdout: StdioCollector {
            onStreamFinished: {
                let lines = text.trim().split("\n")
                root.hasBattery = lines[0] !== "" && !isNaN(parseInt(lines[0]))
                root.batteryPercent = parseInt(lines[0]) || 0
                root.batteryCharging = lines[1] === "Charging" || lines[1] === "Full"
            }
        }
    }

    Timer {
        interval: 30000
        running: true
        repeat: true
        triggeredOnStart: true

        onTriggered: {
            batteryProcess.running = true
        }
    }

    // ─────────────────────────────────────────────
    // Weather
    // ─────────────────────────────────────────────

    Process {
        id: weatherProcess

        command: [
            "sh",
            "-c",
            "curl -s --max-time 5 'wttr.in/?format=%C|%t&u'"
        ]

        stdout: StdioCollector {
            onStreamFinished: {
                const parts = text.trim().split("|")

                if (parts.length === 2 && parts[0] !== "") {
                    root.weatherCondition = parts[0].trim()
                    root.weatherTemp = parts[1].trim().replace(/^\+/, "")
                    root.weatherIcon = root.weatherIconFor(root.weatherCondition)
                }
            }
        }
    }

    Timer {
        interval: 900000
        running: true
        repeat: true
        triggeredOnStart: true

        onTriggered: {
            weatherProcess.running = true
        }
    }

    // ─────────────────────────────────────────────
    // Nextcloud Calendar (next event)
    // ─────────────────────────────────────────────

    // Checks for the Nextcloud CalDAV credentials without invoking
    // calendar-events itself while unauthenticated. The secret holds two
    // lines: Nextcloud username, then an app password.
    Process {
        id: calendarAuthCheckProcess

        command: [
            "sh",
            "-c",
            "test -f /run/agenix/nextcloud-caldav && echo yes || echo no"
        ]

        stdout: StdioCollector {
            onStreamFinished: {
                const authenticated = text.trim() === "yes"
                const justAuthenticated = authenticated && !root.calendarAuthenticated

                root.calendarAuthenticated = authenticated

                if (!authenticated) {
                    root.hasNextEvent = false
                } else if (justAuthenticated) {
                    calendarProcess.running = true
                }
            }
        }
    }

    Process {
        id: calendarProcess

        command: ["calendar-events", "next"]

        stdout: StdioCollector {
            onStreamFinished: {
                let event = null
                try {
                    event = JSON.parse(text.trim())
                } catch (e) {
                    event = null
                }

                if (!event || !event.date) {
                    root.hasNextEvent = false
                    return
                }

                const start = event.time
                    ? new Date(event.date + "T" + event.time + ":00")
                    : new Date(event.date + "T00:00:00")

                if (isNaN(start.getTime())) {
                    root.hasNextEvent = false
                    return
                }

                root.nextEventStartMs = start.getTime()
                root.nextEventTitle = event.title
                root.hasNextEvent = true
            }
        }
    }

    // Lists today's events, for the "today's events" popup
    Process {
        id: todayEventsProcess

        command: ["calendar-events", "events"]

        stdout: StdioCollector {
            onStreamFinished: {
                let events = []
                try {
                    events = JSON.parse(text.trim())
                } catch (e) {
                    events = []
                }

                const today = new Date()
                const todayStr = today.getFullYear() + "-"
                    + String(today.getMonth() + 1).padStart(2, "0") + "-"
                    + String(today.getDate()).padStart(2, "0")

                root.todayEvents = events
                    .filter((e) => e.date === todayStr)
                    .map((e) => {
                        if (!e.time) return { time: "All day", title: e.title }
                        const start = new Date(e.date + "T" + e.time + ":00")
                        const timeLabel = isNaN(start.getTime())
                            ? e.time
                            : Qt.formatDateTime(start, "h:mm AP")
                        return { time: timeLabel, title: e.title }
                    })
            }
        }
    }

    Timer {
        interval: 60000
        running: true
        repeat: true
        triggeredOnStart: true

        onTriggered: {
            calendarAuthCheckProcess.running = true
        }
    }

    Timer {
        interval: 300000
        running: true
        repeat: true

        onTriggered: {
            if (root.calendarAuthenticated) calendarProcess.running = true
        }
    }

    // Redrives the countdown text between calendar refreshes
    Timer {
        interval: 30000
        running: true
        repeat: true

        onTriggered: {
            root.nowMs = Date.now()
        }
    }

    // ─────────────────────────────────────────────
    // Dotfiles Git status
    // ─────────────────────────────────────────────

    Process {
        id: dotfilesStatusProcess

        command: [
            "sh",
            "-c",
            "if git -C \"$HOME/.platform/dotfiles\" diff --quiet --ignore-submodules --; then echo clean; else echo unstaged; fi"
        ]

        stdout: StdioCollector {
            onStreamFinished: {
                root.dotfilesHaveUnstagedChanges = text.trim() === "unstaged"
            }
        }
    }

    Timer {
        interval: 5000
        running: true
        repeat: true
        triggeredOnStart: true

        onTriggered: {
            if (!dotfilesStatusProcess.running) dotfilesStatusProcess.running = true
        }
    }

    // ─────────────────────────────────────────────
    // Todoist (overdue + due today)
    // ─────────────────────────────────────────────

    Process {
        id: tasksProcess

        command: [Quickshell.env("HOME") + "/.platform/dotfiles/scripts/todo", "task", "count"]

        stdout: StdioCollector {
            onStreamFinished: {
                const cols = text.trim().split("\t")

                if (cols.length < 2) {
                    root.hasTasksData = false
                    return
                }

                const overdue = parseInt(cols[0])
                const today = parseInt(cols[1])

                if (isNaN(overdue) || isNaN(today)) {
                    root.hasTasksData = false
                    return
                }

                root.tasksOverdueCount = overdue
                root.tasksDueTodayCount = today
                root.hasTasksData = true
            }
        }
    }

    Timer {
        interval: 300000
        running: true
        repeat: true
        triggeredOnStart: true

        onTriggered: {
            tasksProcess.running = true
        }
    }

    // ─────────────────────────────────────────────
    // Focused Window
    // ─────────────────────────────────────────────

    Connections {
        target: Hyprland

        function onRawEvent(event) {
            if (event.name === "activewindow") {
                let data = event.data.split(",")

                if (data.length >= 2) {
                    root.focusedWindowTitle = data.slice(1).join(",")
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // IPC (bar toggle)
    // ─────────────────────────────────────────────

    IpcHandler {
        target: "bar"

        function toggle(): void {
            root.visible = !root.visible
        }
    }

    IpcHandler {
        target: "wallpaper"

        function toggle(): void {
            root.togglePopup(wallpaperPopup)
        }
    }

    IpcHandler {
        target: "theme"

        function toggle(): void {
            root.togglePopup(themePopup)
        }
    }

    IpcHandler {
        target: "sidepanel"

        function toggle(): void {
            root.togglePopup(sidePanel)
        }
    }

    IpcHandler {
        target: "todoist"

        function toggle(): void {
            root.togglePopup(todoistPanel)
        }
    }

    IpcHandler {
        target: "calendar"

        function toggle(): void {
            root.togglePopup(calendarPanel)
        }
    }

    IpcHandler {
        target: "calendar-fullscreen"

        function toggle(): void {
            root.togglePanelFullscreen(calendarPanel)
        }
    }

    IpcHandler {
        target: "todoist-fullscreen"

        function toggle(): void {
            root.togglePanelFullscreen(todoistPanel)
        }
    }

    IpcHandler {
        target: "sms"

        function toggle(): void {
            root.togglePopup(smsPanel)
        }
    }

    IpcHandler {
        target: "airplane"

        function toggle(): void {
            const adapter = Bluetooth.defaultAdapter
            const radiosOn = (adapter && adapter.enabled) || Networking.wifiEnabled
            const turnOn = !radiosOn

            if (adapter) adapter.enabled = turnOn
            Networking.wifiEnabled = turnOn
        }
    }

    // Bluetooth defaults to off on every quickshell start
    Component.onCompleted: {
        if (Bluetooth.defaultAdapter) Bluetooth.defaultAdapter.enabled = false
    }

    Connections {
        target: Bluetooth

        function onDefaultAdapterChanged() {
            if (Bluetooth.defaultAdapter) Bluetooth.defaultAdapter.enabled = false
        }
    }

    // ─────────────────────────────────────────────
    // VPN (WireGuard)
    // ─────────────────────────────────────────────

    Process {
        id: vpnProcess

        command: ["sh", "-c", "wg show interfaces 2>/dev/null"]

        stdout: StdioCollector {
            onStreamFinished: {
                const active = text.trim().split("\n").filter((s) => s.length > 0)
                root.vpnActiveInterfaces = active
                root.vpnActive = active.length > 0
            }
        }
    }

    Process {
        id: vpnConfigsProcess

        command: ["sh", "-c", "ls /etc/wireguard/*.conf 2>/dev/null | xargs -n1 basename 2>/dev/null | sed 's/\\.conf$//'"]
        running: true

        stdout: StdioCollector {
            onStreamFinished: {
                root.vpnConfigs = text.trim().split("\n").filter((s) => s.length > 0)
            }
        }
    }

    Process {
        id: vpnToggleProcess

        onExited: vpnProcess.running = true
    }

    Timer {
        interval: 5000
        running: true
        repeat: true
        triggeredOnStart: true

        onTriggered: {
            vpnProcess.running = true
            vpnConfigsProcess.running = true
        }
    }

    // ─────────────────────────────────────────────
    // Audio Output / Input Picker
    // ─────────────────────────────────────────────

    PopupWindow {
        id: audioPopup

        anchor.item: volumeArea
        anchor.rect.x: 0
        anchor.rect.y: volumeArea.height + 8
        anchor.rect.width: volumeArea.width
        anchor.rect.height: 0
        anchor.edges: Edges.Bottom | Edges.Right
        anchor.gravity: Edges.Bottom | Edges.Left

        implicitWidth: 300
        implicitHeight: audioColumn.implicitHeight + 16

        color: "transparent"
        visible: false
        grabFocus: true

        onClosed: audioPopup.visible = false

        Rectangle {
            anchors.fill: parent

            radius: 10
            color: root.colBg
            border.color: root.colMuted
            border.width: 1

            Column {
                id: audioColumn

                anchors.fill: parent
                anchors.margins: 8

                spacing: 2

                Text {
                    text: "Output"

                    color: root.colMuted

                    bottomPadding: 4

                    font {
                        family: root.fontFamily
                        pixelSize: root.fontSize - 2
                        bold: true
                    }
                }

                Repeater {
                    model: Pipewire.nodes.values.filter(
                        (node) => node.isSink && !node.isStream
                    )

                    delegate: Rectangle {
                        id: sinkRow

                        required property var modelData

                        width: audioColumn.width
                        height: 28

                        radius: 6
                        color: sinkArea.containsMouse
                            ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.2)
                            : "transparent"

                        MouseArea {
                            id: sinkArea

                            anchors.fill: parent
                            hoverEnabled: true

                            onClicked: {
                                Pipewire.preferredDefaultAudioSink = sinkRow.modelData
                                audioPopup.visible = false
                            }

                            Text {
                                anchors.verticalCenter: parent.verticalCenter
                                anchors.left: parent.left
                                anchors.right: parent.right
                                anchors.leftMargin: 8
                                anchors.rightMargin: 8

                                elide: Text.ElideRight

                                text: {
                                    const label = sinkRow.modelData.description
                                        || sinkRow.modelData.nickname
                                        || sinkRow.modelData.name
                                    const isDefault = Pipewire.defaultAudioSink !== null
                                        && Pipewire.defaultAudioSink.id === sinkRow.modelData.id
                                    return (isDefault ? "󰄬 " : "   ") + label
                                }

                                color: root.colFg

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize
                                }
                            }
                        }
                    }
                }

                Rectangle {
                    width: audioColumn.width
                    height: 1

                    color: root.colMuted
                    opacity: 0.4
                }

                Text {
                    text: "Input"

                    color: root.colMuted

                    topPadding: 8
                    bottomPadding: 4

                    font {
                        family: root.fontFamily
                        pixelSize: root.fontSize - 2
                        bold: true
                    }
                }

                Repeater {
                    model: Pipewire.nodes.values.filter(
                        (node) => (node.type & PwNodeType.Source) !== 0
                            && !node.isStream
                            && !node.name.endsWith(".monitor")
                    )

                    delegate: Rectangle {
                        id: sourceRow

                        required property var modelData

                        width: audioColumn.width
                        height: 28

                        radius: 6
                        color: sourceArea.containsMouse
                            ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.2)
                            : "transparent"

                        MouseArea {
                            id: sourceArea

                            anchors.fill: parent
                            hoverEnabled: true

                            onClicked: {
                                Pipewire.preferredDefaultAudioSource = sourceRow.modelData
                                audioPopup.visible = false
                            }

                            Text {
                                anchors.verticalCenter: parent.verticalCenter
                                anchors.left: parent.left
                                anchors.right: parent.right
                                anchors.leftMargin: 8
                                anchors.rightMargin: 8

                                elide: Text.ElideRight

                                text: {
                                    const label = sourceRow.modelData.description
                                        || sourceRow.modelData.nickname
                                        || sourceRow.modelData.name
                                    const isDefault = Pipewire.defaultAudioSource !== null
                                        && Pipewire.defaultAudioSource.id === sourceRow.modelData.id
                                    return (isDefault ? "󰄬 " : "   ") + label
                                }

                                color: root.colFg

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Wi-Fi Network Picker
    // ─────────────────────────────────────────────

    PopupWindow {
        id: wifiPopup

        anchor.item: wifiArea
        anchor.rect.x: 0
        anchor.rect.y: wifiArea.height + 8
        anchor.rect.width: wifiArea.width
        anchor.rect.height: 0
        anchor.edges: Edges.Bottom | Edges.Right
        anchor.gravity: Edges.Bottom | Edges.Left

        implicitWidth: 300
        implicitHeight: wifiColumn.implicitHeight + 16

        color: "transparent"
        visible: false
        grabFocus: true

        onClosed: wifiPopup.visible = false

        onVisibleChanged: {
            const device = Networking.devices.values.find(
                (d) => d.type === DeviceType.Wifi
            )
            if (device) device.scannerEnabled = visible

            if (!visible) {
                root.wifiPasswordTarget = null
                wifiPasswordInput.text = ""
            }
        }

        Rectangle {
            anchors.fill: parent

            radius: 10
            color: root.colBg
            border.color: root.colMuted
            border.width: 1

            Column {
                id: wifiColumn

                anchors.fill: parent
                anchors.margins: 8

                spacing: 2

                Rectangle {
                    id: wifiToggleRow

                    width: wifiColumn.width
                    height: 28

                    radius: 6
                    color: wifiToggleArea.containsMouse
                        ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.2)
                        : "transparent"

                    MouseArea {
                        id: wifiToggleArea

                        anchors.fill: parent
                        hoverEnabled: true

                        onClicked: {
                            Networking.wifiEnabled = !Networking.wifiEnabled
                        }

                        Text {
                            anchors.verticalCenter: parent.verticalCenter
                            anchors.left: parent.left
                            anchors.right: parent.right
                            anchors.leftMargin: 8
                            anchors.rightMargin: 8

                            text: "Wi-Fi: " + (Networking.wifiEnabled ? "On" : "Off")

                            color: root.colFg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize
                                bold: true
                            }
                        }
                    }
                }

                Rectangle {
                    width: wifiColumn.width
                    height: 1

                    color: root.colMuted
                    opacity: 0.4
                }

                Repeater {
                    model: {
                        const device = Networking.devices.values.find(
                            (d) => d.type === DeviceType.Wifi
                        )
                        if (!device) return []
                        return [...device.networks.values].sort(
                            (a, b) => b.signalStrength - a.signalStrength
                        )
                    }

                    delegate: Rectangle {
                        id: netRow

                        required property var modelData

                        width: wifiColumn.width
                        height: 28

                        radius: 6
                        color: netArea.containsMouse
                            ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.2)
                            : "transparent"

                        MouseArea {
                            id: netArea

                            anchors.fill: parent
                            hoverEnabled: true

                            onClicked: {
                                const net = netRow.modelData

                                if (net.connected) {
                                    net.disconnect()
                                } else if (net.known || net.security === WifiSecurityType.Open) {
                                    root.wifiPasswordTarget = null
                                    net.connect()
                                } else {
                                    root.wifiPasswordTarget = net
                                    wifiPasswordInput.forceActiveFocus()
                                }
                            }

                            Text {
                                anchors.verticalCenter: parent.verticalCenter
                                anchors.left: parent.left
                                anchors.right: parent.right
                                anchors.leftMargin: 8
                                anchors.rightMargin: 8

                                elide: Text.ElideRight

                                text: {
                                    const net = netRow.modelData
                                    const signalIcon = root.wifiIconFor(net)

                                    const lock = net.security === WifiSecurityType.Open ? "" : " 󰌾"
                                    const status = net.stateChanging
                                        ? " …"
                                        : (net.connected ? " 󰄬" : "")

                                    return signalIcon + " " + net.name + lock + status
                                }

                                color: root.colFg

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize
                                }
                            }
                        }
                    }
                }

                Rectangle {
                    width: wifiColumn.width
                    height: root.vpnConfigs.length > 0 ? 1 : 0

                    visible: root.vpnConfigs.length > 0

                    color: root.colMuted
                    opacity: 0.4
                }

                Repeater {
                    model: root.vpnConfigs

                    delegate: Rectangle {
                        id: vpnRow

                        required property string modelData

                        width: wifiColumn.width
                        height: 28

                        radius: 6
                        color: vpnArea.containsMouse
                            ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.2)
                            : "transparent"

                        MouseArea {
                            id: vpnArea

                            anchors.fill: parent
                            hoverEnabled: true

                            onClicked: root.toggleVpn(vpnRow.modelData)

                            Text {
                                anchors.verticalCenter: parent.verticalCenter
                                anchors.left: parent.left
                                anchors.right: parent.right
                                anchors.leftMargin: 8
                                anchors.rightMargin: 8

                                elide: Text.ElideRight

                                text: {
                                    const active = root.vpnActiveInterfaces.includes(vpnRow.modelData)
                                    return "󰑣 " + vpnRow.modelData + (active ? " 󰄬" : "")
                                }

                                color: root.colFg

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize
                                }
                            }
                        }
                    }
                }

                Rectangle {
                    width: wifiColumn.width
                    height: root.wifiPasswordTarget !== null ? 1 : 0

                    visible: root.wifiPasswordTarget !== null

                    color: root.colMuted
                    opacity: 0.4
                }

                Item {
                    width: wifiColumn.width
                    height: root.wifiPasswordTarget !== null ? 32 : 0

                    visible: root.wifiPasswordTarget !== null
                    clip: true

                    Rectangle {
                        anchors.fill: parent
                        anchors.topMargin: 4

                        radius: 6
                        color: Qt.rgba(root.colFg.r, root.colFg.g, root.colFg.b, 0.08)
                        border.color: root.colMuted
                        border.width: 1

                        TextInput {
                            id: wifiPasswordInput

                            anchors.fill: parent
                            anchors.leftMargin: 8
                            anchors.rightMargin: 8
                            verticalAlignment: TextInput.AlignVCenter

                            echoMode: TextInput.Password
                            color: root.colFg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize
                            }

                            Keys.onReturnPressed: {
                                if (root.wifiPasswordTarget) {
                                    root.wifiPasswordTarget.connectWithPsk(wifiPasswordInput.text)
                                }
                                wifiPasswordInput.text = ""
                                root.wifiPasswordTarget = null
                            }

                            Keys.onEscapePressed: {
                                wifiPasswordInput.text = ""
                                root.wifiPasswordTarget = null
                            }

                            Text {
                                visible: wifiPasswordInput.text.length === 0
                                text: "Password, then Enter"
                                color: root.colMuted

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Bluetooth Device Picker
    // ─────────────────────────────────────────────

    PopupWindow {
        id: bluetoothPopup

        anchor.item: bluetoothArea
        anchor.rect.x: 0
        anchor.rect.y: bluetoothArea.height + 8
        anchor.rect.width: bluetoothArea.width
        anchor.rect.height: 0
        anchor.edges: Edges.Bottom | Edges.Right
        anchor.gravity: Edges.Bottom | Edges.Left

        implicitWidth: 300
        implicitHeight: bluetoothColumn.implicitHeight + 16

        color: "transparent"
        visible: false
        grabFocus: true

        onClosed: bluetoothPopup.visible = false

        onVisibleChanged: {
            const adapter = Bluetooth.defaultAdapter
            if (adapter && adapter.enabled) adapter.discovering = visible
        }

        Connections {
            target: Bluetooth.defaultAdapter

            function onStateChanged() {
                const adapter = Bluetooth.defaultAdapter
                if (adapter
                    && adapter.state === BluetoothAdapterState.Enabled
                    && bluetoothPopup.visible) {
                    adapter.discovering = true
                }
            }
        }

        Rectangle {
            anchors.fill: parent

            radius: 10
            color: root.colBg
            border.color: root.colMuted
            border.width: 1

            Column {
                id: bluetoothColumn

                anchors.fill: parent
                anchors.margins: 8

                spacing: 2

                Rectangle {
                    id: adapterToggleRow

                    width: bluetoothColumn.width
                    height: 28

                    radius: 6
                    color: adapterToggleArea.containsMouse
                        ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.2)
                        : "transparent"

                    MouseArea {
                        id: adapterToggleArea

                        anchors.fill: parent
                        hoverEnabled: true

                        onClicked: {
                            const adapter = Bluetooth.defaultAdapter
                            if (adapter) adapter.enabled = !adapter.enabled
                        }

                        Text {
                            anchors.verticalCenter: parent.verticalCenter
                            anchors.left: parent.left
                            anchors.right: parent.right
                            anchors.leftMargin: 8
                            anchors.rightMargin: 8

                            text: {
                                const adapter = Bluetooth.defaultAdapter
                                return "Bluetooth: " + (adapter && adapter.enabled ? "On" : "Off")
                            }

                            color: root.colFg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize
                                bold: true
                            }
                        }
                    }
                }

                Rectangle {
                    width: bluetoothColumn.width
                    height: 1

                    color: root.colMuted
                    opacity: 0.4
                }

                Repeater {
                    model: {
                        const adapter = Bluetooth.defaultAdapter
                        if (!adapter) return []
                        const macAddress = /^([0-9A-F]{2}[:-]){5}[0-9A-F]{2}$/i
                        return [...adapter.devices.values]
                            .filter((d) => d.name && d.name.length > 0 && !macAddress.test(d.name))
                            .sort((a, b) => {
                                if (a.connected !== b.connected) return a.connected ? -1 : 1
                                if (a.paired !== b.paired) return a.paired ? -1 : 1
                                return a.name.localeCompare(b.name)
                            })
                    }

                    delegate: Rectangle {
                        id: btRow

                        required property var modelData

                        width: bluetoothColumn.width
                        height: 28

                        radius: 6
                        color: btArea.containsMouse
                            ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.2)
                            : "transparent"

                        MouseArea {
                            id: btArea

                            anchors.fill: parent
                            hoverEnabled: true

                            onClicked: {
                                const dev = btRow.modelData
                                if (dev.connected) {
                                    dev.disconnect()
                                } else if (dev.paired) {
                                    dev.connect()
                                } else {
                                    dev.pair()
                                }
                            }

                            Text {
                                anchors.verticalCenter: parent.verticalCenter
                                anchors.left: parent.left
                                anchors.right: parent.right
                                anchors.leftMargin: 8
                                anchors.rightMargin: 8

                                elide: Text.ElideRight

                                text: {
                                    const dev = btRow.modelData
                                    const battery = dev.batteryAvailable
                                        ? " (" + Math.round(dev.battery * 100) + "%)"
                                        : ""

                                    let status = ""
                                    if (dev.pairing
                                        || dev.state === BluetoothDeviceState.Connecting
                                        || dev.state === BluetoothDeviceState.Disconnecting) {
                                        status = " …"
                                    } else if (dev.connected) {
                                        status = " 󰄬"
                                    } else if (!dev.paired) {
                                        status = " (tap to pair)"
                                    }

                                    return dev.name + battery + status
                                }

                                color: root.colFg

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Nextcloud Calendar (today's events)
    // ─────────────────────────────────────────────

    PopupWindow {
        id: calendarPopup

        anchor.item: calendarArea
        anchor.rect.x: 0
        anchor.rect.y: -8
        anchor.rect.width: calendarArea.width
        anchor.rect.height: 0
        anchor.edges: Edges.Top | Edges.Left
        anchor.gravity: Edges.Top | Edges.Right

        implicitWidth: 280
        implicitHeight: calendarColumn.implicitHeight + 16

        color: "transparent"
        visible: false
        grabFocus: true

        onClosed: calendarPopup.visible = false

        onVisibleChanged: {
            if (visible) todayEventsProcess.running = true
        }

        Rectangle {
            anchors.fill: parent

            radius: 10
            color: root.colBg
            border.color: root.colMuted
            border.width: 1

            Column {
                id: calendarColumn

                anchors.fill: parent
                anchors.margins: 8

                spacing: 2

                Text {
                    text: "Today's Events"

                    color: root.colMuted

                    bottomPadding: 4

                    font {
                        family: root.fontFamily
                        pixelSize: root.fontSize - 2
                        bold: true
                    }
                }

                Text {
                    visible: root.todayEvents.length === 0

                    text: "No events today"

                    color: root.colMuted

                    font {
                        family: root.fontFamily
                        pixelSize: root.fontSize
                    }
                }

                Repeater {
                    model: root.todayEvents

                    delegate: Item {
                        id: eventRow

                        required property var modelData

                        width: calendarColumn.width
                        height: eventRowLayout.implicitHeight + 6

                        RowLayout {
                            id: eventRowLayout

                            anchors.left: parent.left
                            anchors.right: parent.right
                            anchors.verticalCenter: parent.verticalCenter

                            spacing: 8

                            Text {
                                text: eventRow.modelData.time

                                color: root.colAccent

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize - 1
                                    bold: true
                                }
                            }

                            Text {
                                Layout.fillWidth: true

                                text: eventRow.modelData.title

                                elide: Text.ElideRight

                                color: root.colFg

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize - 1
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Wallpaper Picker
    // ─────────────────────────────────────────────

    PanelWindow {
        id: wallpaperPopup

        property string selectedTheme: ""
        property var themes: []
        property var files: []
        property int selectedIndex: 0

        readonly property var filteredThemes: wallpaperPopup.themes.filter(
            (t) => t.toLowerCase().includes(wallpaperSearchInput.text.toLowerCase())
        )
        readonly property var filteredFiles: wallpaperPopup.files.filter(
            (f) => wallpaperPopup.basename(f).toLowerCase()
                .includes(wallpaperSearchInput.text.toLowerCase())
        )

        function basename(path) {
            return path.substring(path.lastIndexOf("/") + 1)
        }

        function openTheme(theme) {
            wallpaperPopup.selectedTheme = theme
            wallpaperPopup.selectedIndex = 0
            wallpaperSearchInput.text = ""
            wallpaperFilesProcess.command = [
                "sh", "-c",
                'find "$1" -maxdepth 1 -type f \\( -iname "*.jpg" -o -iname "*.jpeg" -o -iname "*.png" -o -iname "*.webp" -o -iname "*.gif" \\) | sort',
                "_",
                Quickshell.env("HOME") + "/.wallpapers/" + theme
            ]
            wallpaperFilesProcess.running = true
        }

        function backToThemes() {
            wallpaperPopup.selectedTheme = ""
            wallpaperPopup.selectedIndex = 0
            wallpaperSearchInput.text = ""
        }

        function applyWallpaper(path) {
            wallpaperSetProcess.command = ["set-wallpaper", path]
            wallpaperSetProcess.running = true
        }

        function moveSelection(delta) {
            const count = wallpaperPopup.selectedTheme === ""
                ? wallpaperPopup.filteredThemes.length
                : wallpaperPopup.filteredFiles.length

            if (count === 0) {
                wallpaperPopup.selectedIndex = 0
                return
            }

            let next = wallpaperPopup.selectedIndex + delta
            if (next < 0) next = 0
            if (next >= count) next = count - 1
            wallpaperPopup.selectedIndex = next
        }

        function activateSelection() {
            if (wallpaperPopup.selectedTheme === "") {
                const theme = wallpaperPopup.filteredThemes[wallpaperPopup.selectedIndex]
                if (theme) wallpaperPopup.openTheme(theme)
            } else {
                const file = wallpaperPopup.filteredFiles[wallpaperPopup.selectedIndex]
                if (file) wallpaperPopup.applyWallpaper(file)
            }
        }

        WlrLayershell.layer: WlrLayer.Overlay
        WlrLayershell.keyboardFocus: WlrKeyboardFocus.Exclusive
        WlrLayershell.namespace: "wallpaper-picker"

        anchors.top: true
        anchors.bottom: true
        anchors.left: true
        anchors.right: true

        exclusionMode: ExclusionMode.Ignore

        color: "transparent"
        visible: false

        onVisibleChanged: {
            if (visible) {
                wallpaperPopup.selectedTheme = ""
                wallpaperPopup.selectedIndex = 0
                wallpaperSearchInput.text = ""
                wallpaperThemesProcess.running = true
                Qt.callLater(() => wallpaperSearchInput.forceActiveFocus())
            }
        }

        Process {
            id: wallpaperThemesProcess

            command: [
                "sh", "-c",
                'find "$1" -mindepth 1 -maxdepth 1 -type d ! -name ".*" -printf "%f\\n" | sort',
                "_",
                Quickshell.env("HOME") + "/.wallpapers"
            ]

            stdout: StdioCollector {
                onStreamFinished: {
                    wallpaperPopup.themes = text.trim().split("\n").filter((t) => t.length > 0)
                }
            }
        }

        Process {
            id: wallpaperFilesProcess

            stdout: StdioCollector {
                onStreamFinished: {
                    wallpaperPopup.files = text.trim().split("\n").filter((f) => f.length > 0)
                }
            }
        }

        Process {
            id: wallpaperSetProcess
        }

        MouseArea {
            anchors.fill: parent

            onClicked: wallpaperPopup.visible = false

            Rectangle {
                anchors.fill: parent
                color: Qt.rgba(0, 0, 0, 0.35)
            }
        }

        Rectangle {
            anchors.centerIn: parent

            width: 460
            height: 520

            radius: 10
            color: root.colBg
            border.color: root.colMuted
            border.width: 1

            MouseArea {
                anchors.fill: parent
                onClicked: {}
            }

            Rectangle {
                id: wallpaperHeader

                anchors.top: parent.top
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.margins: 8

                height: 28

                radius: 6
                color: headerArea.containsMouse && wallpaperPopup.selectedTheme !== ""
                    ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.2)
                    : "transparent"

                MouseArea {
                    id: headerArea

                    anchors.fill: parent
                    hoverEnabled: true
                    enabled: wallpaperPopup.selectedTheme !== ""

                    onClicked: {
                        wallpaperPopup.backToThemes()
                    }

                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        anchors.left: parent.left
                        anchors.right: parent.right
                        anchors.leftMargin: 8
                        anchors.rightMargin: 8

                        elide: Text.ElideRight

                        text: wallpaperPopup.selectedTheme === ""
                            ? "Choose a Theme"
                            : "‹ " + wallpaperPopup.selectedTheme

                        color: root.colFg

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize
                            bold: true
                        }
                    }
                }
            }

            Rectangle {
                id: wallpaperSearchBox

                anchors.top: wallpaperHeader.bottom
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.margins: 8
                anchors.topMargin: 4

                height: 28

                radius: 6
                color: Qt.rgba(root.colFg.r, root.colFg.g, root.colFg.b, 0.08)
                border.color: root.colMuted
                border.width: 1

                TextInput {
                    id: wallpaperSearchInput

                    anchors.fill: parent
                    anchors.leftMargin: 8
                    anchors.rightMargin: 8
                    verticalAlignment: TextInput.AlignVCenter

                    color: root.colFg

                    font {
                        family: root.fontFamily
                        pixelSize: root.fontSize
                    }

                    onTextChanged: {
                        wallpaperPopup.selectedIndex = 0
                    }

                    Keys.onEscapePressed: {
                        if (wallpaperSearchInput.text.length > 0) {
                            wallpaperSearchInput.text = ""
                        } else {
                            wallpaperPopup.visible = false
                        }
                    }

                    Keys.onUpPressed: (event) => {
                        wallpaperPopup.moveSelection(
                            wallpaperPopup.selectedTheme === "" ? -1 : -wallpaperGrid.columns
                        )
                        event.accepted = true
                    }

                    Keys.onDownPressed: (event) => {
                        wallpaperPopup.moveSelection(
                            wallpaperPopup.selectedTheme === "" ? 1 : wallpaperGrid.columns
                        )
                        event.accepted = true
                    }

                    Keys.onLeftPressed: (event) => {
                        if (wallpaperPopup.selectedTheme !== "") {
                            wallpaperPopup.moveSelection(-1)
                            event.accepted = true
                        }
                    }

                    Keys.onRightPressed: (event) => {
                        if (wallpaperPopup.selectedTheme !== "") {
                            wallpaperPopup.moveSelection(1)
                            event.accepted = true
                        }
                    }

                    Keys.onTabPressed: (event) => {
                        wallpaperPopup.moveSelection(1)
                        event.accepted = true
                    }

                    Keys.onBacktabPressed: (event) => {
                        wallpaperPopup.moveSelection(-1)
                        event.accepted = true
                    }

                    Keys.onReturnPressed: (event) => {
                        wallpaperPopup.activateSelection()
                        event.accepted = true
                    }

                    Keys.onEnterPressed: (event) => {
                        wallpaperPopup.activateSelection()
                        event.accepted = true
                    }

                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        anchors.left: parent.left

                        visible: wallpaperSearchInput.text.length === 0

                        text: wallpaperPopup.selectedTheme === ""
                            ? "Search themes…"
                            : "Search wallpapers…"

                        color: root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize
                        }
                    }
                }
            }

            ListView {
                anchors.top: wallpaperSearchBox.bottom
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.bottom: parent.bottom
                anchors.margins: 8
                anchors.topMargin: 4

                visible: wallpaperPopup.selectedTheme === ""
                clip: true

                spacing: 2

                model: wallpaperPopup.filteredThemes
                currentIndex: wallpaperPopup.selectedIndex

                delegate: Rectangle {
                    id: themeRow

                    required property string modelData
                    required property int index

                    width: ListView.view.width
                    height: 28

                    radius: 6
                    color: (themeArea.containsMouse || themeRow.index === wallpaperPopup.selectedIndex)
                        ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.2)
                        : "transparent"

                    MouseArea {
                        id: themeArea

                        anchors.fill: parent
                        hoverEnabled: true

                        onClicked: {
                            wallpaperPopup.openTheme(themeRow.modelData)
                        }

                        Text {
                            anchors.verticalCenter: parent.verticalCenter
                            anchors.left: parent.left
                            anchors.right: parent.right
                            anchors.leftMargin: 8
                            anchors.rightMargin: 8

                            elide: Text.ElideRight

                            text: themeRow.modelData
                                .replace(/[-_]/g, " ")
                                .replace(/\b\w/g, (c) => c.toUpperCase())

                            color: root.colFg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize
                            }
                        }
                    }
                }
            }

            GridView {
                id: wallpaperGrid

                anchors.top: wallpaperSearchBox.bottom
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.bottom: parent.bottom
                anchors.margins: 8
                anchors.topMargin: 4

                visible: wallpaperPopup.selectedTheme !== ""
                clip: true

                cellWidth: 128
                cellHeight: 96

                readonly property int columns: Math.max(1, Math.floor(width / cellWidth))

                model: wallpaperPopup.filteredFiles
                currentIndex: wallpaperPopup.selectedIndex

                delegate: Item {
                    id: fileCell

                    required property string modelData
                    required property int index

                    width: GridView.view.cellWidth
                    height: GridView.view.cellHeight

                    ClippingRectangle {
                        anchors.fill: parent
                        anchors.margins: 4

                        radius: 6
                        color: root.colMuted

                        border.color: (fileArea.containsMouse || fileCell.index === wallpaperPopup.selectedIndex)
                            ? root.colAccent
                            : "transparent"
                        border.width: 2

                        Image {
                            anchors.fill: parent

                            source: "file://" + fileCell.modelData
                            fillMode: Image.PreserveAspectCrop
                            asynchronous: true

                            sourceSize.width: 128
                            sourceSize.height: 96
                        }
                    }

                    MouseArea {
                        id: fileArea

                        anchors.fill: parent
                        hoverEnabled: true

                        onClicked: {
                            wallpaperPopup.applyWallpaper(fileCell.modelData)
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Theme Picker
    // ─────────────────────────────────────────────
    //
    // No rofi/wofi/similar launcher exists anywhere in this dotfiles
    // setup (hyprlauncher is the app launcher, not a generic menu), so
    // this follows the wallpaperPopup pattern already established just
    // above instead of adding a new picker dependency: a PanelWindow
    // listing `theme-set`'s targets, built the same way (flat here --
    // themes don't have wallpaperPopup's theme/file two-level nesting).

    PanelWindow {
        id: themePopup

        property var themes: []
        property int selectedIndex: 0

        readonly property var filteredThemes: themePopup.themes.filter(
            (t) => t.toLowerCase().includes(themeSearchInput.text.toLowerCase())
        )

        function applyTheme(name) {
            themeSetProcess.command = ["theme-set", name]
            themeSetProcess.running = true
            themePopup.visible = false
        }

        function moveSelection(delta) {
            const count = themePopup.filteredThemes.length
            if (count === 0) {
                themePopup.selectedIndex = 0
                return
            }
            let next = themePopup.selectedIndex + delta
            if (next < 0) next = 0
            if (next >= count) next = count - 1
            themePopup.selectedIndex = next
        }

        function activateSelection() {
            const name = themePopup.filteredThemes[themePopup.selectedIndex]
            if (name) themePopup.applyTheme(name)
        }

        WlrLayershell.layer: WlrLayer.Overlay
        WlrLayershell.keyboardFocus: WlrKeyboardFocus.Exclusive
        WlrLayershell.namespace: "theme-picker"

        anchors.top: true
        anchors.bottom: true
        anchors.left: true
        anchors.right: true

        exclusionMode: ExclusionMode.Ignore

        color: "transparent"
        visible: false

        onVisibleChanged: {
            if (visible) {
                themePopup.selectedIndex = 0
                themeSearchInput.text = ""
                themeListProcess.running = true
                Qt.callLater(() => themeSearchInput.forceActiveFocus())
            }
        }

        Process {
            id: themeListProcess

            // Repo themes first, then any machine-local ones under
            // ~/.config/themes -- same resolution order as theme-common.sh's
            // theme_list(), kept here as a plain shell one-liner (same
            // reasoning as wallpaperThemesProcess above: no new script
            // needed just to list files).
            command: [
                "sh", "-c",
                'find "$1" "$2" -mindepth 1 -maxdepth 1 -name "*.json" -printf "%f\\n" 2>/dev/null | sed "s/\\.json$//" | sort -u',
                "_",
                Quickshell.env("HOME") + "/.platform/dotfiles/config/themes",
                Quickshell.env("HOME") + "/.config/themes"
            ]

            stdout: StdioCollector {
                onStreamFinished: {
                    themePopup.themes = text.trim().split("\n").filter((t) => t.length > 0)
                }
            }
        }

        Process {
            id: themeSetProcess
        }

        MouseArea {
            anchors.fill: parent

            onClicked: themePopup.visible = false

            Rectangle {
                anchors.fill: parent
                color: Qt.rgba(0, 0, 0, 0.35)
            }
        }

        Rectangle {
            anchors.centerIn: parent

            width: 360
            height: 420

            radius: 10
            color: root.colBg
            border.color: root.colMuted
            border.width: 1

            MouseArea {
                anchors.fill: parent
                onClicked: {}
            }

            Rectangle {
                id: themeHeader

                anchors.top: parent.top
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.margins: 8

                height: 28

                radius: 6
                color: "transparent"

                Text {
                    anchors.verticalCenter: parent.verticalCenter
                    anchors.left: parent.left
                    anchors.leftMargin: 8

                    text: "Choose a Theme"

                    color: root.colFg

                    font {
                        family: root.fontFamily
                        pixelSize: root.fontSize
                        bold: true
                    }
                }
            }

            Rectangle {
                id: themeSearchBox

                anchors.top: themeHeader.bottom
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.margins: 8
                anchors.topMargin: 4

                height: 28

                radius: 6
                color: Qt.rgba(root.colFg.r, root.colFg.g, root.colFg.b, 0.08)
                border.color: root.colMuted
                border.width: 1

                TextInput {
                    id: themeSearchInput

                    anchors.fill: parent
                    anchors.leftMargin: 8
                    anchors.rightMargin: 8
                    verticalAlignment: TextInput.AlignVCenter

                    color: root.colFg

                    font {
                        family: root.fontFamily
                        pixelSize: root.fontSize
                    }

                    onTextChanged: {
                        themePopup.selectedIndex = 0
                    }

                    Keys.onEscapePressed: {
                        if (themeSearchInput.text.length > 0) {
                            themeSearchInput.text = ""
                        } else {
                            themePopup.visible = false
                        }
                    }

                    Keys.onUpPressed: (event) => {
                        themePopup.moveSelection(-1)
                        event.accepted = true
                    }

                    Keys.onDownPressed: (event) => {
                        themePopup.moveSelection(1)
                        event.accepted = true
                    }

                    Keys.onTabPressed: (event) => {
                        themePopup.moveSelection(1)
                        event.accepted = true
                    }

                    Keys.onBacktabPressed: (event) => {
                        themePopup.moveSelection(-1)
                        event.accepted = true
                    }

                    Keys.onReturnPressed: (event) => {
                        themePopup.activateSelection()
                        event.accepted = true
                    }

                    Keys.onEnterPressed: (event) => {
                        themePopup.activateSelection()
                        event.accepted = true
                    }

                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        anchors.left: parent.left

                        visible: themeSearchInput.text.length === 0

                        text: "Search themes…"

                        color: root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize
                        }
                    }
                }
            }

            ListView {
                anchors.top: themeSearchBox.bottom
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.bottom: parent.bottom
                anchors.margins: 8
                anchors.topMargin: 4

                clip: true
                spacing: 2

                model: themePopup.filteredThemes
                currentIndex: themePopup.selectedIndex

                delegate: Rectangle {
                    id: themePickRow

                    required property string modelData
                    required property int index

                    width: ListView.view.width
                    height: 28

                    radius: 6
                    color: (themePickRowArea.containsMouse || themePickRow.index === themePopup.selectedIndex)
                        ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.2)
                        : "transparent"

                    MouseArea {
                        id: themePickRowArea

                        anchors.fill: parent
                        hoverEnabled: true

                        onClicked: {
                            themePopup.applyTheme(themePickRow.modelData)
                        }

                        Text {
                            anchors.verticalCenter: parent.verticalCenter
                            anchors.left: parent.left
                            anchors.right: parent.right
                            anchors.leftMargin: 8
                            anchors.rightMargin: 8

                            elide: Text.ElideRight

                            text: themePickRow.modelData
                                .replace(/[-_]/g, " ")
                                .replace(/\b\w/g, (c) => c.toUpperCase())

                            color: root.colFg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize
                            }
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Side Panel (Lights / WiFi / Bluetooth / Sound / Settings)
    // ─────────────────────────────────────────────

    PanelWindow {
        id: sidePanel

        // ---- Lights state (migrated verbatim from the old lightsPopup) ----

        property var lightsState: ({})
        property bool lightsLoading: true
        property string lightsError: ""

        property var livingRoomLights: [
            { name: "Main Lights", entity: "light.main_lights" },
            { name: "Living Room Lamp", entity: "light.living_room_lamp" },
            { name: "Christmas Tree", entity: "light.living_room_floor_lamp" }
        ]
        property var livingRoomScenes: [
            { name: "Chill Vibes", entity: "scene.chill_vibes" },
            { name: "Normal", entity: "scene.living_room_normal" },
            { name: "Movie", entity: "scene.living_room_movie" },
            { name: "Bright", entity: "scene.living_room_bright" },
            { name: "Red Moody", entity: "scene.living_room_red_moody" },
            { name: "Early Morning", entity: "scene.living_room_early_morning" }
        ]
        property var bedroomLights: [
            { name: "Main", entity: "light.bedroom_main_light_1" },
            { name: "Desk", entity: "light.desk_light" },
            { name: "Salt Lamp", entity: "light.salt_lamp_plug", hasSlider: false }
        ]
        property var bedroomScenes: [
            { name: "Normal", entity: "scene.bedroom_normal" },
            { name: "Relax", entity: "scene.bedroom_relax" },
            { name: "Sleep", entity: "scene.bedroom_sleep" },
            { name: "Morning", entity: "scene.bedroom_morning" },
            { name: "Reading", entity: "scene.bedroom_reading" },
            { name: "Night Red", entity: "scene.bedroom_night_red" },
            { name: "Wake Up", entity: "scene.bedroom_wake_up" },
            { name: "Cozy", entity: "scene.bedroom_cozy" }
        ]

        function fetchLightsState() {
            lightsStatesProcess.running = true
        }

        function runLightsAction(args) {
            lightsActionProcess.command = args
            lightsActionProcess.running = true
        }

        WlrLayershell.layer: WlrLayer.Top
        WlrLayershell.keyboardFocus: WlrKeyboardFocus.OnDemand
        WlrLayershell.namespace: "side-panel"

        anchors.top: true
        anchors.bottom: true
        anchors.right: true

        implicitWidth: 380
        margins.top: 0

        color: root.colBg
        visible: false

        onVisibleChanged: {
            if (visible) {
                sidePanel.lightsError = ""
                sidePanel.lightsLoading = true
                sidePanel.fetchLightsState()
            }
        }

        // ---- Lights backend (ha-lights, unchanged from the old lightsPopup) ----

        Process {
            id: lightsStatesProcess

            command: ["ha-lights", "states"]

            stdout: StdioCollector {
                id: lightsStatesStdout
            }
            stderr: StdioCollector {
                id: lightsStatesStderr
            }

            onExited: (exitCode) => {
                sidePanel.lightsLoading = false
                if (exitCode === 0) {
                    try {
                        sidePanel.lightsState = JSON.parse(lightsStatesStdout.text)
                        sidePanel.lightsError = ""
                    } catch (error) {
                        sidePanel.lightsError = "Failed to parse lights state"
                    }
                } else {
                    const firstLine = lightsStatesStderr.text.trim().split("\n")[0]
                    sidePanel.lightsError = firstLine || "ha-lights states failed"
                }
            }
        }

        Process {
            id: lightsActionProcess

            stderr: StdioCollector {
                id: lightsActionStderr
            }

            onExited: (exitCode) => {
                if (exitCode !== 0) {
                    const firstLine = lightsActionStderr.text.trim().split("\n")[0]
                    sidePanel.lightsError = firstLine || "ha-lights action failed"
                }
                sidePanel.fetchLightsState()
            }
        }

        // ---- Shared Lights components (migrated verbatim) ----

        Component {
            id: lightRowComponent

            Rectangle {
                id: lightRow

                required property var modelData

                readonly property string entityId: lightRow.modelData.entity
                readonly property bool hasSlider: lightRow.modelData.hasSlider !== false
                readonly property var entityState: sidePanel.lightsState[lightRow.entityId]
                readonly property bool known: lightRow.entityState !== undefined && lightRow.entityState !== null
                readonly property bool isOn: lightRow.known && lightRow.entityState.state === "on"
                readonly property int statePct: (lightRow.known
                    && lightRow.entityState.brightness_pct !== null
                    && lightRow.entityState.brightness_pct !== undefined)
                    ? lightRow.entityState.brightness_pct : 0

                property int dragPct: -1
                readonly property int displayPct: lightRow.dragPct >= 0 ? lightRow.dragPct : lightRow.statePct

                width: parent.width
                height: 34

                color: "transparent"

                Text {
                    id: lightRowName

                    anchors.left: parent.left
                    anchors.verticalCenter: parent.verticalCenter
                    width: 92

                    text: lightRow.modelData.name
                    elide: Text.ElideRight

                    color: lightRow.known ? root.colFg : root.colMuted

                    font {
                        family: root.fontFamily
                        pixelSize: root.fontSize - 1
                    }
                }

                Rectangle {
                    id: lightToggle

                    anchors.left: lightRowName.right
                    anchors.leftMargin: 6
                    anchors.verticalCenter: parent.verticalCenter

                    width: 38
                    height: 20
                    radius: 10

                    color: (lightRow.known && lightRow.isOn) ? root.colAccent : "transparent"
                    border.color: lightRow.known ? root.colAccent : root.colMuted
                    border.width: 1

                    Rectangle {
                        width: 14
                        height: 14
                        radius: 7
                        anchors.verticalCenter: parent.verticalCenter
                        x: (lightRow.known && lightRow.isOn) ? parent.width - width - 3 : 3

                        color: (lightRow.known && lightRow.isOn) ? root.colBg : root.colMuted
                    }

                    MouseArea {
                        anchors.fill: parent
                        enabled: lightRow.known

                        onClicked: sidePanel.runLightsAction(["ha-lights", "toggle", lightRow.entityId])
                    }
                }

                Rectangle {
                    id: lightSliderTrack

                    visible: lightRow.hasSlider

                    anchors.left: lightToggle.right
                    anchors.leftMargin: 8
                    anchors.right: lightSliderPct.left
                    anchors.rightMargin: 6
                    anchors.verticalCenter: parent.verticalCenter

                    height: 6
                    radius: 3
                    color: Qt.rgba(root.colFg.r, root.colFg.g, root.colFg.b, 0.12)

                    Rectangle {
                        anchors.left: parent.left
                        anchors.top: parent.top
                        anchors.bottom: parent.bottom

                        width: Math.max(0, Math.min(lightSliderTrack.width,
                            lightSliderTrack.width * lightRow.displayPct / 100))

                        radius: 3
                        color: lightRow.known
                            ? (lightRow.isOn ? root.colAccent : root.colMuted)
                            : root.colMuted
                    }

                    Rectangle {
                        width: 12
                        height: 12
                        radius: 6
                        anchors.verticalCenter: parent.verticalCenter
                        visible: lightRow.known

                        x: Math.max(0, Math.min(lightSliderTrack.width - width,
                            lightSliderTrack.width * lightRow.displayPct / 100 - width / 2))

                        color: root.colFg
                    }

                    MouseArea {
                        id: lightSliderArea

                        anchors.fill: parent
                        enabled: lightRow.known

                        onPressed: (mouse) => {
                            const ratio = Math.max(0, Math.min(1, mouse.x / lightSliderArea.width))
                            lightRow.dragPct = Math.round(ratio * 100)
                        }

                        onPositionChanged: (mouse) => {
                            if (!lightSliderArea.pressed) return
                            const ratio = Math.max(0, Math.min(1, mouse.x / lightSliderArea.width))
                            lightRow.dragPct = Math.round(ratio * 100)
                        }

                        onReleased: (mouse) => {
                            const ratio = Math.max(0, Math.min(1, mouse.x / lightSliderArea.width))
                            const finalPct = Math.round(ratio * 100)
                            lightRow.dragPct = -1
                            sidePanel.runLightsAction(["ha-lights", "brightness", lightRow.entityId, String(finalPct)])
                        }
                    }
                }

                Text {
                    id: lightSliderPct

                    anchors.right: parent.right
                    anchors.verticalCenter: parent.verticalCenter
                    width: 34

                    horizontalAlignment: Text.AlignRight

                    text: !lightRow.known
                        ? "unknown"
                        : (!lightRow.hasSlider
                            ? (lightRow.isOn ? "on" : "off")
                            : (lightRow.isOn ? (lightRow.displayPct + "%") : "off"))

                    color: root.colMuted

                    font {
                        family: root.fontFamily
                        pixelSize: root.fontSize - 2
                    }
                }
            }
        }

        Component {
            id: sceneButtonComponent

            Rectangle {
                id: sceneButton

                required property var modelData

                width: (parent.width - parent.columnSpacing) / 2
                height: 26

                radius: 6
                color: sceneButtonArea.containsMouse
                    ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.25)
                    : Qt.rgba(root.colFg.r, root.colFg.g, root.colFg.b, 0.06)
                border.color: root.colMuted
                border.width: 1

                MouseArea {
                    id: sceneButtonArea

                    anchors.fill: parent
                    hoverEnabled: true

                    onClicked: sidePanel.runLightsAction(["ha-lights", "scene", sceneButton.modelData.entity])

                    Text {
                        anchors.centerIn: parent

                        text: sceneButton.modelData.name
                        elide: Text.ElideRight

                        color: root.colFg

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize - 2
                        }
                    }
                }
            }
        }

        // ---- Chrome: thin left border matching the bar-popup borders ----

        Rectangle {
            anchors.left: parent.left
            anchors.top: parent.top
            anchors.bottom: parent.bottom
            width: 1
            color: root.colMuted
            opacity: 0.4
        }

        ColumnLayout {
            id: sidePanelLayout

            anchors.fill: parent
            anchors.margins: 8

            spacing: 8

            Flickable {
                id: lightsFlick

                Layout.fillWidth: true
                Layout.fillHeight: true

                contentWidth: width
                contentHeight: lightsContent.implicitHeight
                clip: true

                Column {
                    id: lightsContent

                    width: lightsFlick.width
                    spacing: 6

                    Row {
                        width: lightsContent.width
                        spacing: 8

                        Text {
                            text: "Lights"
                            color: root.colFg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize
                                bold: true
                            }
                        }

                        Text {
                            visible: sidePanel.lightsError.length > 0
                            width: lightsContent.width - 60
                            elide: Text.ElideRight

                            text: sidePanel.lightsError
                            color: root.colRed

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize - 2
                            }
                        }
                    }

                    Text {
                        visible: sidePanel.lightsLoading
                        text: "Loading…"
                        color: root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize
                        }
                    }

                    Column {
                        visible: !sidePanel.lightsLoading
                        width: lightsContent.width
                        spacing: 6

                        Text {
                            text: "Living Room"
                            color: root.colAccent

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize
                                bold: true
                            }
                        }

                        Repeater {
                            model: sidePanel.livingRoomLights
                            delegate: lightRowComponent
                        }

                        Rectangle {
                            width: parent.width
                            height: 1
                            color: root.colMuted
                            opacity: 0.4
                        }

                        Text {
                            text: "Scenes"
                            topPadding: 6
                            bottomPadding: 2
                            color: root.colMuted

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize - 2
                                bold: true
                            }
                        }

                        Grid {
                            width: parent.width
                            columns: 2
                            columnSpacing: 6
                            rowSpacing: 6

                            Repeater {
                                model: sidePanel.livingRoomScenes
                                delegate: sceneButtonComponent
                            }
                        }

                        Text {
                            text: "Bedroom"
                            topPadding: 10
                            color: root.colAccent

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize
                                bold: true
                            }
                        }

                        Repeater {
                            model: sidePanel.bedroomLights
                            delegate: lightRowComponent
                        }

                        Rectangle {
                            width: parent.width
                            height: 1
                            color: root.colMuted
                            opacity: 0.4
                        }

                        Text {
                            text: "Scenes"
                            topPadding: 6
                            bottomPadding: 2
                            color: root.colMuted

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize - 2
                                bold: true
                            }
                        }

                        Grid {
                            width: parent.width
                            columns: 2
                            columnSpacing: 6
                            rowSpacing: 6

                            Repeater {
                                model: sidePanel.bedroomScenes
                                delegate: sceneButtonComponent
                            }
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Todoist Panel (today + overdue tasks)
    // ─────────────────────────────────────────────

    PanelWindow {
        id: todoistPanel

        property var tasksList: []
        property bool tasksLoading: true
        property string tasksError: ""

        // Fullscreen mode (task fullscreen key via IPC): sized like the
        // pypr scratchpads (92% of the screen, 4% left/right margins, 40px
        // from the top) instead of filling the whole screen. See
        // anchors/margins below. Side mode is the normal 380px panel.
        property bool fullscreen: false

        function fetchTasks() {
            todoistTasksProcess.running = true
        }

        function completeTask(id) {
            todoistCompleteProcess.command = ["todoist-tasks", "complete", id]
            todoistCompleteProcess.running = true
        }

        function addTask() {
            const content = todoistAddContentField.text.trim()
            if (!content) return
            const due = todoistAddDueField.text.trim()
            const cmd = ["todoist-tasks", "add", content]
            if (due) cmd.push(due)
            todoistAddProcess.command = cmd
            todoistAddProcess.running = true
        }

        // has_time tasks carry a full ISO datetime; date-only tasks are
        // just "Today" / "Overdue" since there's no time to show.
        function formatDue(task) {
            if (task.has_time) {
                const d = new Date(task.due)
                const label = isNaN(d.getTime()) ? task.due : Qt.formatDateTime(d, "h:mm AP")
                return task.overdue ? ("Overdue " + label) : label
            }
            return task.overdue ? "Overdue" : "Today"
        }

        WlrLayershell.layer: WlrLayer.Top
        WlrLayershell.keyboardFocus: WlrKeyboardFocus.OnDemand
        WlrLayershell.namespace: "todoist-panel"

        anchors.top: true
        anchors.bottom: true
        anchors.right: true
        anchors.left: fullscreen

        implicitWidth: 380
        // Fullscreen geometry: anchored top+bottom with margins, so the panel sits flush under the 30px bar (root.implicitHeight) and leaves a 4% gap at the bottom.
        implicitHeight: fullscreen ? Math.round((screen ? screen.height : 1080) * 0.92) : 0
        margins.left: fullscreen ? Math.round((screen ? screen.width : 1920) * 0.04) : 0
        margins.right: fullscreen ? Math.round((screen ? screen.width : 1920) * 0.04) : 0
        margins.top: fullscreen ? root.implicitHeight : 0
        margins.bottom: fullscreen ? Math.round((screen ? screen.height : 1080) * 0.04) : 0

        color: root.colBg
        visible: false

        onVisibleChanged: {
            if (visible) {
                todoistPanel.tasksError = ""
                todoistPanel.tasksLoading = true
                todoistPanel.fetchTasks()
            }
        }

        Process {
            id: todoistTasksProcess

            command: ["todoist-tasks", "tasks"]

            stdout: StdioCollector {
                id: todoistTasksStdout
            }
            stderr: StdioCollector {
                id: todoistTasksStderr
            }

            onExited: (exitCode) => {
                todoistPanel.tasksLoading = false
                if (exitCode === 0) {
                    try {
                        todoistPanel.tasksList = JSON.parse(todoistTasksStdout.text)
                        todoistPanel.tasksError = ""
                    } catch (error) {
                        todoistPanel.tasksError = "Failed to parse tasks"
                    }
                } else {
                    const firstLine = todoistTasksStderr.text.trim().split("\n")[0]
                    todoistPanel.tasksError = firstLine || "todoist-tasks failed"
                }
            }
        }

        Process {
            id: todoistCompleteProcess

            stderr: StdioCollector {
                id: todoistCompleteStderr
            }

            onExited: (exitCode) => {
                if (exitCode !== 0) {
                    const firstLine = todoistCompleteStderr.text.trim().split("\n")[0]
                    todoistPanel.tasksError = firstLine || "todoist-tasks complete failed"
                }
                todoistPanel.fetchTasks()
            }
        }

        Process {
            id: todoistAddProcess

            stdout: StdioCollector {
                id: todoistAddStdout
            }
            stderr: StdioCollector {
                id: todoistAddStderr
            }

            onExited: (exitCode) => {
                if (exitCode === 0) {
                    todoistAddContentField.text = ""
                    todoistAddDueField.text = ""
                    todoistPanel.tasksError = ""
                    todoistPanel.fetchTasks()
                } else {
                    todoistPanel.tasksError = todoistAddStderr.text.trim() || "Failed to add task"
                }
            }
        }

        Component {
            id: todoistTaskRowComponent

            Rectangle {
                id: taskRow

                required property var modelData

                width: parent.width
                height: taskRowLayout.implicitHeight + 10

                radius: 6
                color: taskRowArea.containsMouse
                    ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.15)
                    : "transparent"

                MouseArea {
                    id: taskRowArea

                    anchors.fill: parent
                    hoverEnabled: true

                    onClicked: todoistPanel.completeTask(taskRow.modelData.id)

                    ColumnLayout {
                        id: taskRowLayout

                        anchors.left: parent.left
                        anchors.right: parent.right
                        anchors.verticalCenter: parent.verticalCenter
                        anchors.leftMargin: 6
                        anchors.rightMargin: 6

                        spacing: 2

                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 6

                            Text {
                                text: "○"
                                color: taskRow.modelData.overdue ? root.colRed : root.colMuted

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize
                                }
                            }

                            Text {
                                Layout.fillWidth: true

                                text: taskRow.modelData.content
                                elide: Text.ElideRight

                                color: root.colFg

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize - 1
                                }
                            }
                        }

                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 6

                            Text {
                                Layout.fillWidth: true

                                text: taskRow.modelData.project
                                elide: Text.ElideRight

                                color: root.colMuted

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize - 2
                                }
                            }

                            Text {
                                text: todoistPanel.formatDue(taskRow.modelData)

                                color: taskRow.modelData.overdue ? root.colRed : root.colAccent

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize - 2
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Chrome: thin left border matching the bar-popup borders ----

        Rectangle {
            anchors.left: parent.left
            anchors.top: parent.top
            anchors.bottom: parent.bottom
            width: 1
            color: root.colMuted
            opacity: 0.4
        }

        ColumnLayout {
            id: todoistPanelLayout

            anchors.fill: parent
            anchors.margins: 8

            spacing: 8

            Flickable {
                id: tasksFlick

                Layout.fillWidth: true
                Layout.fillHeight: true

                contentWidth: width
                contentHeight: tasksContent.implicitHeight
                clip: true

                Column {
                    id: tasksContent

                    width: tasksFlick.width
                    spacing: 6

                    RowLayout {
                        width: tasksContent.width
                        spacing: 8

                        Text {
                            text: "Tasks"
                            color: root.colFg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize
                                bold: true
                            }
                        }

                        Text {
                            Layout.fillWidth: true

                            visible: todoistPanel.tasksError.length > 0
                            elide: Text.ElideRight

                            text: todoistPanel.tasksError
                            color: root.colRed

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize - 2
                            }
                        }

                        Item {
                            Layout.fillWidth: true
                            visible: todoistPanel.tasksError.length === 0
                        }

                        MouseArea {
                            id: refreshArea

                            implicitWidth: refreshText.implicitWidth
                            implicitHeight: refreshText.implicitHeight
                            hoverEnabled: true

                            onClicked: todoistPanel.fetchTasks()

                            Text {
                                id: refreshText

                                text: "⟳"
                                color: refreshArea.containsMouse ? root.colFg : root.colMuted

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize
                                    bold: true
                                }
                            }
                        }
                    }

                    Text {
                        visible: todoistPanel.tasksLoading
                        text: "Loading…"
                        color: root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize
                        }
                    }

                    Text {
                        visible: !todoistPanel.tasksLoading
                            && todoistPanel.tasksList.length === 0
                            && todoistPanel.tasksError.length === 0

                        text: "Nothing due today or overdue."
                        color: root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize
                        }
                    }

                    Column {
                        visible: !todoistPanel.tasksLoading

                        width: tasksContent.width
                        spacing: 4

                        Repeater {
                            model: todoistPanel.tasksList
                            delegate: todoistTaskRowComponent
                        }
                    }

                    Column {
                        width: tasksContent.width
                        spacing: 6

                        TextField {
                            id: todoistAddContentField

                            width: parent.width

                            placeholderText: "Add task…"
                            placeholderTextColor: root.colMuted
                            color: root.colFg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize - 1
                            }

                            background: Rectangle {
                                color: "transparent"
                                border.color: root.colMuted
                                border.width: 1
                                radius: 6
                            }

                            onAccepted: todoistPanel.addTask()
                        }

                        RowLayout {
                            width: parent.width
                            spacing: 6

                            TextField {
                                id: todoistAddDueField

                                Layout.preferredWidth: 120

                                placeholderText: "YYYY-MM-DD"
                                placeholderTextColor: root.colMuted
                                color: root.colFg

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize - 1
                                }

                                background: Rectangle {
                                    color: "transparent"
                                    border.color: root.colMuted
                                    border.width: 1
                                    radius: 6
                                }

                                onAccepted: todoistPanel.addTask()
                            }

                            Text {
                                Layout.fillWidth: true

                                text: "blank = no due date"
                                color: root.colMuted

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize - 2
                                }
                            }

                            MouseArea {
                                id: todoistAddButtonArea

                                implicitWidth: todoistAddButtonText.implicitWidth
                                implicitHeight: todoistAddButtonText.implicitHeight
                                hoverEnabled: true

                                onClicked: todoistPanel.addTask()

                                Text {
                                    id: todoistAddButtonText

                                    text: "Add"
                                    color: todoistAddButtonArea.containsMouse ? root.colAccent : root.colFg

                                    font {
                                        family: root.fontFamily
                                        pixelSize: root.fontSize
                                        bold: true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Calendar Panel (day view: navigate days, add events)
    // ─────────────────────────────────────────────

    PanelWindow {
        id: calendarPanel

        property var eventsList: []
        property bool eventsLoading: true
        property string eventsError: ""
        property int dayOffset: 0

        // Fullscreen mode (SUPER+ALT+C via IPC): sized like the pypr
        // scratchpads (92% of the screen, 4% left/right margins, 40px from
        // the top) instead of filling the whole screen. See anchors/margins
        // below. Side mode is the normal 380px right-side panel.
        property bool fullscreen: false

        // Set on open so the first fetch auto-advances to the next day with
        // events when today is empty. Cleared after that fetch.
        property bool autoAdvanceOnLoad: false

        // Selected day as "YYYY-MM-DD"; recomputed when dayOffset changes.
        property string selectedDateStr: {
            const d = new Date()
            d.setDate(d.getDate() + dayOffset)
            return Qt.formatDate(d, "yyyy-MM-dd")
        }

        // Events for the selected day only. No time filtering: past events
        // earlier in the day still show.
        property var dayEvents: eventsList.filter(function(e) { return e.date === selectedDateStr })

        // The 7 days (Monday..Sunday) of the selected day's week, as
        // "YYYY-MM-DD" strings. Used for the fullscreen week view.
        property var weekDays: {
            const sel = new Date(selectedDateStr + "T00:00:00")
            const mondayOffset = (sel.getDay() + 6) % 7
            const monday = new Date(sel)
            monday.setDate(sel.getDate() - mondayOffset)
            const days = []
            for (let i = 0; i < 7; i++) {
                const d = new Date(monday)
                d.setDate(monday.getDate() + i)
                days.push(Qt.formatDate(d, "yyyy-MM-dd"))
            }
            return days
        }

        // One entry per week day: {date, events}. No time filtering.
        property var weekModel: weekDays.map(function(ds) {
            return { date: ds, events: eventsList.filter(function(e) { return e.date === ds }) }
        })

        function fetchEvents() {
            calendarEventsProcess.running = true
        }

        function selectedDayLabel() {
            if (dayOffset === 0) return "Today"
            if (dayOffset === 1) return "Tomorrow"
            if (dayOffset === -1) return "Yesterday"
            const d = new Date()
            d.setDate(d.getDate() + dayOffset)
            return Qt.formatDate(d, "ddd MMM d")
        }

        // Header label for the fullscreen week view, e.g. "Nov 30 – Dec 6".
        function weekRangeLabel() {
            const first = new Date(weekDays[0] + "T00:00:00")
            const last = new Date(weekDays[6] + "T00:00:00")
            return Qt.formatDate(first, "MMM d") + " – " + Qt.formatDate(last, "MMM d")
        }

        // Label for one day in the week view, e.g. "Mon Nov 30".
        // Today gets a "Today · " prefix so it stands out.
        function weekDayLabel(dateStr) {
            const d = new Date(dateStr + "T00:00:00")
            const label = Qt.formatDate(d, "ddd MMM d")
            const todayStr = Qt.formatDate(new Date(), "yyyy-MM-dd")
            return dateStr === todayStr ? "Today · " + label : label
        }

        // Advance dayOffset forward to the next day (today..+13) that has at
        // least one event. Used on open so the panel lands on a day with
        // events instead of an empty today. Stays at 0 if no day has events.
        function advanceToNextEventDay() {
            for (let off = 0; off <= 13; off++) {
                const d = new Date()
                d.setDate(d.getDate() + off)
                const ds = Qt.formatDate(d, "yyyy-MM-dd")
                if (eventsList.some(function(e) { return e.date === ds })) {
                    dayOffset = off
                    return
                }
            }
            dayOffset = 0
        }

        function stepDay(delta) {
            // In the fullscreen week view a click moves a whole week.
            const step = fullscreen ? 7 : 1
            dayOffset = Math.max(-7, Math.min(13, dayOffset + delta * step))
        }

        function addEvent() {
            const title = calendarAddTitleField.text.trim()
            if (!title) return
            const time = calendarAddTimeField.text.trim()
            const cmd = ["calendar-events", "add", title, selectedDateStr]
            if (time) cmd.push(time)
            calendarAddProcess.command = cmd
            calendarAddProcess.running = true
        }

        function timeLabel(event) {
            if (!event.time) return "All day"
            const d = new Date(event.date + "T" + event.time + ":00")
            return isNaN(d.getTime()) ? event.time : Qt.formatDateTime(d, "h:mm AP")
        }

        WlrLayershell.layer: WlrLayer.Top
        WlrLayershell.keyboardFocus: WlrKeyboardFocus.OnDemand
        WlrLayershell.namespace: "calendar-panel"

        anchors.top: true
        anchors.bottom: true
        anchors.right: true
        anchors.left: fullscreen

        implicitWidth: 380
        // Fullscreen geometry: anchored top+bottom with margins, so the panel sits flush under the 30px bar (root.implicitHeight) and leaves a 4% gap at the bottom.
        implicitHeight: fullscreen ? Math.round((screen ? screen.height : 1080) * 0.92) : 0
        margins.left: fullscreen ? Math.round((screen ? screen.width : 1920) * 0.04) : 0
        margins.right: fullscreen ? Math.round((screen ? screen.width : 1920) * 0.04) : 0
        margins.top: fullscreen ? root.implicitHeight : 0
        margins.bottom: fullscreen ? Math.round((screen ? screen.height : 1080) * 0.04) : 0

        color: root.colBg
        visible: false

        onVisibleChanged: {
            if (visible) {
                calendarPanel.eventsError = ""
                // Always start from today on open; the first fetch then
                // auto-advances to the next day with events if today is empty.
                calendarPanel.dayOffset = 0
                calendarPanel.autoAdvanceOnLoad = true
                if (root.calendarAuthenticated) {
                    calendarPanel.eventsLoading = true
                    calendarPanel.fetchEvents()
                } else {
                    calendarPanel.eventsLoading = false
                    calendarPanel.autoAdvanceOnLoad = false
                }
            }
        }

        Process {
            id: calendarEventsProcess

            command: ["calendar-events", "events", "21", "-7"]

            stdout: StdioCollector {
                id: calendarEventsStdout
            }
            stderr: StdioCollector {
                id: calendarEventsStderr
            }

            onExited: (exitCode) => {
                calendarPanel.eventsLoading = false
                if (exitCode === 0) {
                    try {
                        calendarPanel.eventsList = JSON.parse(calendarEventsStdout.text)
                        calendarPanel.eventsError = ""
                        if (calendarPanel.autoAdvanceOnLoad) {
                            calendarPanel.advanceToNextEventDay()
                        }
                    } catch (error) {
                        calendarPanel.eventsError = "Failed to parse events"
                    }
                } else {
                    const firstLine = calendarEventsStderr.text.trim().split("\n")[0]
                    calendarPanel.eventsError = firstLine || "calendar-events failed"
                }
                calendarPanel.autoAdvanceOnLoad = false
            }
        }

        Process {
            id: calendarAddProcess

            stdout: StdioCollector {
                id: calendarAddStdout
            }
            stderr: StdioCollector {
                id: calendarAddStderr
            }

            onExited: (exitCode) => {
                if (exitCode === 0) {
                    calendarAddTitleField.text = ""
                    calendarAddTimeField.text = ""
                    calendarPanel.eventsError = ""
                    calendarPanel.fetchEvents()
                } else {
                    const firstLine = calendarAddStderr.text.trim().split("\n")[0]
                    calendarPanel.eventsError = firstLine || "Failed to add event"
                }
            }
        }

        Component {
            id: calendarEventRowComponent

            Rectangle {
                id: calEventRow

                required property var modelData

                width: parent.width
                height: calEventRowLayout.implicitHeight + 10

                radius: 6
                color: "transparent"

                ColumnLayout {
                    id: calEventRowLayout

                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.verticalCenter: parent.verticalCenter
                    anchors.leftMargin: 6
                    anchors.rightMargin: 6

                    spacing: 2

                    RowLayout {
                        Layout.fillWidth: true
                        spacing: 6

                        Text {
                            text: calendarPanel.timeLabel(calEventRow.modelData)
                            color: root.colAccent

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize - 2
                                bold: true
                            }
                        }

                        Text {
                            Layout.fillWidth: true

                            text: calEventRow.modelData.title
                            elide: Text.ElideRight

                            color: root.colFg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize - 1
                            }
                        }
                    }

                    Text {
                        Layout.fillWidth: true

                        text: calEventRow.modelData.calendar
                        elide: Text.ElideRight

                        color: root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize - 2
                        }
                    }
                }
            }
        }

        Component {
            id: calendarWeekDayComponent

            // One day column in the fullscreen 7-column week view.
            // Fills an equal share of the row and stretches to the
            // tallest day so the divider spans the full week height.
            Item {
                id: calWeekDay

                required property var modelData // {date, events}

                Layout.fillWidth: true
                Layout.fillHeight: true
                implicitHeight: calWeekDayColumn.implicitHeight

                Column {
                    id: calWeekDayColumn

                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.top: parent.top
                    anchors.rightMargin: 8

                    spacing: 4

                    Text {
                        width: parent.width
                        elide: Text.ElideRight

                        text: calendarPanel.weekDayLabel(calWeekDay.modelData.date)
                        color: root.colFg

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize
                            bold: true
                        }
                    }

                    Repeater {
                        model: calWeekDay.modelData.events
                        delegate: calendarEventRowComponent
                    }

                    Text {
                        visible: calWeekDay.modelData.events.length === 0

                        width: parent.width
                        elide: Text.ElideRight

                        text: "No events"
                        color: root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize - 1
                        }
                    }
                }

                // Divider between day columns (not after the last one).
                Rectangle {
                    visible: index < 6

                    anchors.right: parent.right
                    anchors.top: parent.top
                    anchors.bottom: parent.bottom
                    width: 1
                    color: root.colMuted
                    opacity: 0.4
                }
            }
        }

        // ---- Chrome: thin left border matching the bar-popup borders ----

        Rectangle {
            anchors.left: parent.left
            anchors.top: parent.top
            anchors.bottom: parent.bottom
            width: 1
            color: root.colMuted
            opacity: 0.4
        }

        ColumnLayout {
            id: calendarPanelLayout

            anchors.fill: parent
            anchors.margins: 8

            spacing: 8

            Flickable {
                id: calendarEventsFlick

                Layout.fillWidth: true
                Layout.fillHeight: true

                contentWidth: width
                contentHeight: calendarEventsContent.implicitHeight
                clip: true

                Column {
                    id: calendarEventsContent

                    width: calendarEventsFlick.width
                    spacing: 6

                    RowLayout {
                        width: calendarEventsContent.width
                        spacing: 8

                        Text {
                            text: "Calendar"
                            color: root.colFg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize
                                bold: true
                            }
                        }

                        MouseArea {
                            id: calendarPanelPrevArea

                            implicitWidth: calendarPanelPrevText.implicitWidth
                            implicitHeight: calendarPanelPrevText.implicitHeight
                            hoverEnabled: true
                            opacity: calendarPanel.dayOffset <= -7 ? 0.35 : 1

                            onClicked: calendarPanel.stepDay(-1)

                            Text {
                                id: calendarPanelPrevText

                                text: "<"
                                color: calendarPanelPrevArea.containsMouse ? root.colFg : root.colMuted

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize
                                    bold: true
                                }
                            }
                        }

                        Text {
                            text: calendarPanel.fullscreen ? calendarPanel.weekRangeLabel() : calendarPanel.selectedDayLabel()
                            color: root.colFg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize
                                bold: true
                            }
                        }

                        MouseArea {
                            id: calendarPanelNextArea

                            implicitWidth: calendarPanelNextText.implicitWidth
                            implicitHeight: calendarPanelNextText.implicitHeight
                            hoverEnabled: true
                            opacity: calendarPanel.dayOffset >= 13 ? 0.35 : 1

                            onClicked: calendarPanel.stepDay(1)

                            Text {
                                id: calendarPanelNextText

                                text: ">"
                                color: calendarPanelNextArea.containsMouse ? root.colFg : root.colMuted

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize
                                    bold: true
                                }
                            }
                        }

                        Text {
                            Layout.fillWidth: true

                            visible: calendarPanel.eventsError.length > 0
                            elide: Text.ElideRight

                            text: calendarPanel.eventsError
                            color: root.colRed

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize - 2
                            }
                        }

                        Item {
                            Layout.fillWidth: true
                            visible: calendarPanel.eventsError.length === 0
                        }

                        MouseArea {
                            id: calendarPanelRefreshArea

                            implicitWidth: calendarPanelRefreshText.implicitWidth
                            implicitHeight: calendarPanelRefreshText.implicitHeight
                            hoverEnabled: true

                            onClicked: calendarPanel.fetchEvents()

                            Text {
                                id: calendarPanelRefreshText

                                text: "⟳"
                                color: calendarPanelRefreshArea.containsMouse ? root.colFg : root.colMuted

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize
                                    bold: true
                                }
                            }
                        }
                    }

                    Text {
                        visible: !root.calendarAuthenticated

                        text: "Nextcloud calendar not set up yet."
                        color: root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize
                        }
                    }

                    Text {
                        visible: root.calendarAuthenticated && calendarPanel.eventsLoading
                        text: "Loading…"
                        color: root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize
                        }
                    }

                    Text {
                        visible: root.calendarAuthenticated
                            && !calendarPanel.fullscreen
                            && !calendarPanel.eventsLoading
                            && calendarPanel.dayEvents.length === 0
                            && calendarPanel.eventsError.length === 0

                        text: "No events on this day."
                        color: root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize
                        }
                    }

                    // Side mode: just the selected day.
                    Column {
                        visible: root.calendarAuthenticated && !calendarPanel.fullscreen && !calendarPanel.eventsLoading

                        width: calendarEventsContent.width
                        spacing: 4

                        Repeater {
                            model: calendarPanel.dayEvents
                            delegate: calendarEventRowComponent
                        }
                    }

                    // Fullscreen mode: the whole week (Monday..Sunday)
                    // as 7 columns, one per day, like Google Calendar's
                    // week view. Columns share the width equally.
                    RowLayout {
                        visible: root.calendarAuthenticated && calendarPanel.fullscreen && !calendarPanel.eventsLoading

                        width: calendarEventsContent.width
                        spacing: 0

                        Repeater {
                            model: calendarPanel.weekModel
                            delegate: calendarWeekDayComponent
                        }
                    }

                    // Add-event form: one compact row — title expands, a
                    // small time field, and a "+" button. Same function as
                    // before (title + optional HH:MM, blank = all day).
                    Rectangle {
                        visible: root.calendarAuthenticated

                        width: calendarEventsContent.width
                        height: calendarAddRow.implicitHeight + 12

                        radius: 8
                        color: Qt.rgba(root.colMuted.r, root.colMuted.g, root.colMuted.b, 0.08)
                        border.color: root.colMuted
                        border.width: 1

                        RowLayout {
                            id: calendarAddRow

                            anchors.fill: parent
                            anchors.leftMargin: 10
                            anchors.rightMargin: 10
                            anchors.topMargin: 6
                            anchors.bottomMargin: 6

                            spacing: 8

                            TextField {
                                id: calendarAddTitleField

                                Layout.fillWidth: true

                                placeholderText: "Add event…"
                                placeholderTextColor: root.colMuted
                                color: root.colFg

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize - 1
                                }

                                background: Item {}

                                onAccepted: calendarPanel.addEvent()
                            }

                            TextField {
                                id: calendarAddTimeField

                                Layout.preferredWidth: 64

                                placeholderText: "HH:MM"
                                placeholderTextColor: root.colMuted
                                color: root.colFg

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize - 1
                                }

                                background: Item {}

                                onAccepted: calendarPanel.addEvent()
                            }

                            MouseArea {
                                id: calendarAddButtonArea

                                implicitWidth: calendarAddButtonText.implicitWidth + 8
                                implicitHeight: calendarAddButtonText.implicitHeight
                                hoverEnabled: true

                                onClicked: calendarPanel.addEvent()

                                Text {
                                    id: calendarAddButtonText

                                    anchors.centerIn: parent

                                    text: "+"
                                    color: calendarAddButtonArea.containsMouse ? root.colAccent : root.colMuted

                                    font {
                                        family: root.fontFamily
                                        pixelSize: root.fontSize + 2
                                        bold: true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // SMS Panel (KDE Connect, v1 -- parallel toggle alongside the existing
    // SUPER+P kdeconnect-sms scratchpad; does not replace it yet)
    // ─────────────────────────────────────────────

    PanelWindow {
        id: smsPanel

        // "", "no-daemon", "no-device", "not-paired", "unreachable",
        // "sms-disabled", or "ok" -- see kdeconnect-sms-dbus status
        property string deviceState: ""
        property string deviceId: ""
        property string deviceName: ""
        property string statusError: ""
        property string conversationsError: ""
        property string sendError: ""
        property bool sendPending: false

        // threadId(string) -> latest message object, for the conversation list
        property var threads: ({})
        // sorted (newest-first) cache of Object.keys(threads); recomputed
        // whenever threads changes so the Repeater model is a plain array
        property var threadIdList: []
        // normalized 10-digit number(string) -> contact name, from the vcard
        // cache kdeconnectd's own contacts plugin already maintains
        property var contactNames: ({})

        // "" = conversation list is showing; otherwise the threadId open
        property string openThreadId: ""
        // uid(string) -> message object, only populated for the open thread
        property var openThreadMessages: ({})
        // threadId(string) -> {uid(string): message}, for EVERY thread the
        // watcher has seen messages for -- survives closing the thread view
        // so reopening shows cached messages instantly while paging tops it up
        property var threadMessageCache: ({})

        // Thread summaries staged by watch events, waiting for the
        // coalescing flush -- applying them one at a time rebuilds the
        // conversation list once per signal, which is a storm during the
        // cold-start burst. See flushThreadSummaries().
        property var pendingThreadSummaries: ({})

        // Conversation-list search text (see threadMatchesFilter).
        property string threadFilter: ""

        // New-message flow state (see the new-message view below).
        property bool smsNewMessage: false
        property var newMessageContact: null // {number, name} once picked
        property string newMessageSearch: ""
        property string newMessageText: ""
        property bool sendPendingNew: false

        // Contacts as a sorted list (the number -> name map lives in
        // contactNames; this is the picker model for new messages).
        property var contactList: []

        // Attachment downloads: uniqueId -> local file path once the
        // daemon's attachmentReceived signal reports it ready, plus the
        // set already requested (so a bubble re-created by a list rebuild
        // doesn't re-request) and the serial request queue.
        property var attachmentFiles: ({})
        property var attachmentRequested: ({})
        property var attachmentQueue: []

        // threadId currently being paged through history, or "" if no
        // paging is in flight; guards stray timer/signal callbacks after
        // the user switches threads or closes the view mid-page
        property string threadPagingThreadId: ""
        property int threadFetchPageIndex: 0
        // openThreadMessages count captured right before the in-flight
        // page's request, to detect whether that page added anything
        property int threadFetchPrevCount: 0
        readonly property int threadFetchPageSize: 50
        readonly property int threadFetchMaxPages: 6

        // True while the open thread's view sits at (or near) the newest
        // messages; cleared when the user scrolls up, so paging growth
        // stops yanking the view back to the bottom mid-read.
        property bool smsThreadNearBottom: true

        function normalizeNumber(addr) {
            const digits = (addr || "").replace(/[^0-9]/g, "")
            return digits.length > 10 ? digits.slice(-10) : digits
        }

        function displayNameFor(addresses) {
            if (!addresses || addresses.length === 0) return "Unknown"
            return addresses
                .map((a) => smsPanel.contactNames[smsPanel.normalizeNumber(a)] || a)
                .join(", ")
        }

        function refreshThreadIdList() {
            const ids = Object.keys(smsPanel.threads)
            ids.sort((a, b) => smsPanel.threads[b].date - smsPanel.threads[a].date)
            smsPanel.threadIdList = ids
        }

        // Shared by every conversationCreated/conversationUpdated event
        // off the watch stream. Events are STAGED, not applied: the flush
        // timer below applies a whole burst in one copy + one sort, so
        // the conversation list rebuilds a few times instead of once per
        // signal. The date guard lives here and in the flush, so a
        // late-arriving older backfill message can't clobber the current
        // snippet at either stage.
        function mergeThreadSummary(msg) {
            if (!msg || msg.threadId === undefined || msg.threadId === null
                || msg.uid === undefined || msg.uid === null) return
            const key = String(msg.threadId)
            const newest = smsPanel.pendingThreadSummaries[key] || smsPanel.threads[key]
            if (newest && msg.date < newest.date) return
            const next = Object.assign({}, smsPanel.pendingThreadSummaries)
            next[key] = msg
            smsPanel.pendingThreadSummaries = next
            threadListFlushTimer.restart()
        }

        function flushThreadSummaries() {
            threadListFlushTimer.stop()
            const pending = smsPanel.pendingThreadSummaries
            const keys = Object.keys(pending)
            if (keys.length === 0) return
            smsPanel.pendingThreadSummaries = {}
            const next = Object.assign({}, smsPanel.threads)
            for (const key of keys) {
                const existing = next[key]
                if (existing && pending[key].date < existing.date) continue
                next[key] = pending[key]
            }
            smsPanel.threads = next
            smsPanel.refreshThreadIdList()
        }

        // Whole-list merge for the initial activeConversations() fetch:
        // one copy + one sort for the entire list instead of one per
        // thread.
        function mergeThreadSummaries(list) {
            const next = Object.assign({}, smsPanel.threads)
            for (const msg of list) {
                if (!msg || msg.threadId === undefined || msg.threadId === null
                    || msg.uid === undefined || msg.uid === null) continue
                const key = String(msg.threadId)
                const existing = next[key]
                if (existing && msg.date < existing.date) continue
                next[key] = msg
            }
            smsPanel.threads = next
            smsPanel.refreshThreadIdList()
        }

        function removeThread(threadId) {
            if (threadId === undefined || threadId === null) return
            const key = String(threadId)
            if (key in smsPanel.pendingThreadSummaries) {
                const pending = Object.assign({}, smsPanel.pendingThreadSummaries)
                delete pending[key]
                smsPanel.pendingThreadSummaries = pending
            }
            if (!(key in smsPanel.threads)) return
            const next = Object.assign({}, smsPanel.threads)
            delete next[key]
            smsPanel.threads = next
            smsPanel.refreshThreadIdList()
            if (smsPanel.openThreadId === key) smsPanel.closeThreadView()
        }

        // Keyed by uid so a malformed signal missing either id is dropped
        // before it can create a phantom message; an empty body with a
        // valid uid is a real MMS and still renders normally.
        function mergeOpenMessage(msg) {
            if (!msg || msg.threadId === undefined || msg.threadId === null
                || msg.uid === undefined || msg.uid === null) return
            const threadKey = String(msg.threadId)
            const uidKey = String(msg.uid)

            // Every thread keeps a cache, not just the one currently open,
            // so switching threads later shows cached messages instantly.
            const threadCache = Object.assign({}, smsPanel.threadMessageCache[threadKey] || {})
            threadCache[uidKey] = msg
            const nextCache = Object.assign({}, smsPanel.threadMessageCache)
            nextCache[threadKey] = threadCache
            smsPanel.threadMessageCache = nextCache

            if (threadKey !== smsPanel.openThreadId) return
            if (smsPanel.threadPagingThreadId !== "") {
                // Paging in flight: a burst of backfill signals is coming.
                // Coalesce so the message list rebuilds a few times, not
                // once per message; live messages (no paging) still render
                // immediately via the else branch.
                openThreadFlushTimer.restart()
            } else {
                smsPanel.openThreadMessages = threadCache
            }
        }

        function flushOpenThreadMessages() {
            openThreadFlushTimer.stop()
            if (smsPanel.openThreadId === "") return
            smsPanel.openThreadMessages = smsPanel.threadMessageCache[smsPanel.openThreadId] || {}
        }

        // --- Conversation search: name, number, or snippet text ---
        function threadMatchesFilter(threadId) {
            const f = smsPanel.threadFilter.trim().toLowerCase()
            if (f === "") return true
            const msg = smsPanel.threads[threadId]
            if (!msg) return false
            if (smsPanel.displayNameFor(msg.addresses).toLowerCase().includes(f)) return true
            const fdigits = f.replace(/\D/g, "")
            if (fdigits !== "") {
                for (const addr of (msg.addresses || [])) {
                    if (String(addr).replace(/\D/g, "").includes(fdigits)) return true
                }
            }
            return (msg.body || "").toLowerCase().includes(f)
        }

        function visibleThreadIds() {
            return smsPanel.threadIdList.filter(id => smsPanel.threadMatchesFilter(id))
        }

        // --- New messages ---
        function filteredContacts() {
            const f = smsPanel.newMessageSearch.trim().toLowerCase()
            const fdigits = smsPanel.newMessageSearch.replace(/\D/g, "")
            return smsPanel.contactList.filter(entry => {
                if (f === "") return true
                if (entry.name.toLowerCase().includes(f)) return true
                return fdigits !== "" && entry.number.includes(fdigits)
            })
        }

        // A typed number with no matching contact, offered as a direct
        // "Text <digits>" row in the picker; "" when not applicable.
        function newMessageRawNumber() {
            const d = smsPanel.newMessageSearch.replace(/\D/g, "")
            if (d.length < 7) return ""
            for (const entry of smsPanel.contactList) if (entry.number === d) return ""
            return d
        }

        function openNewMessage() {
            smsPanel.closeThreadView()
            smsPanel.smsNewMessage = true
            smsPanel.newMessageContact = null
            smsPanel.newMessageSearch = ""
            smsPanel.newMessageText = ""
            smsPanel.sendError = ""
            newMessageSearchInput.text = ""
            newMessageInput.text = ""
        }

        function closeNewMessage() {
            smsPanel.smsNewMessage = false
            smsPanel.newMessageContact = null
            smsPanel.sendPendingNew = false
        }

        function goBack() {
            if (smsPanel.smsNewMessage) {
                if (smsPanel.newMessageContact) smsPanel.newMessageContact = null
                else smsPanel.closeNewMessage()
            } else {
                smsPanel.closeThreadView()
            }
        }

        function sendNewMessage() {
            const contact = smsPanel.newMessageContact
            const text = smsPanel.newMessageText.trim()
            if (!contact || text === "" || smsPanel.deviceState !== "ok" || smsPanel.deviceId === "") return
            smsPanel.sendPendingNew = true
            smsPanel.sendError = ""
            sendProcess.command = ["kdeconnect-sms-dbus", "newsms", smsPanel.deviceId, contact.number, text]
            sendProcess.running = true
        }

        // --- Attachments: one request at a time; the file itself arrives
        // via the attachmentReceived signal on the watch stream ---
        function requestAttachment(partId, uniqueId) {
            if (!uniqueId || smsPanel.attachmentFiles[uniqueId] || smsPanel.attachmentRequested[uniqueId]) return
            const req = Object.assign({}, smsPanel.attachmentRequested)
            req[uniqueId] = true
            smsPanel.attachmentRequested = req
            smsPanel.attachmentQueue = smsPanel.attachmentQueue.concat([{partId: partId, uniqueId: uniqueId}])
            smsPanel.pumpAttachmentQueue()
        }

        function pumpAttachmentQueue() {
            if (attachmentProcess.running || smsPanel.attachmentQueue.length === 0) return
            if (smsPanel.deviceState !== "ok" || smsPanel.deviceId === "") return
            const next = smsPanel.attachmentQueue[0]
            smsPanel.attachmentQueue = smsPanel.attachmentQueue.slice(1)
            attachmentProcess.command = ["kdeconnect-sms-dbus", "attachment", smsPanel.deviceId, String(next.partId), next.uniqueId]
            attachmentProcess.running = true
        }

        function sortedOpenMessages() {
            const vals = Object.values(smsPanel.openThreadMessages)
            vals.sort((a, b) => a.date - b.date)
            return vals
        }

        function openThreadView(threadId) {
            const key = String(threadId)
            smsPanel.openThreadId = key
            // Show whatever's cached immediately; paging below tops it up.
            smsPanel.openThreadMessages = smsPanel.threadMessageCache[key] || {}
            smsPanel.sendError = ""
            smsPanel.startThreadPaging(key)
            Qt.callLater(() => composeInput.forceActiveFocus())
            // The thread Flickable is shared across threads and keeps its
            // old scroll position; jump to the newest messages on open.
            // Later growth is handled by the near-bottom guard on the
            // Flickable itself (see smsThreadFlick below).
            Qt.callLater(() => {
                smsPanel.smsThreadNearBottom = true
                smsThreadFlick.contentY = Math.max(0, smsThreadFlick.contentHeight - smsThreadFlick.height)
            })
        }

        function closeThreadView() {
            smsPanel.stopThreadPaging()
            smsPanel.openThreadId = ""
            smsPanel.openThreadMessages = {}
        }

        // Pages backwards through a thread's history 50 messages at a time
        // (cap 6 pages / 300 messages) until a page adds no new uids.
        // requestConversation only backfills from the phone ONCE per call
        // and never loops server-side, so the paging has to happen here.
        function startThreadPaging(threadId) {
            smsPanel.threadPagingThreadId = threadId
            smsPanel.threadFetchPageIndex = 0
            smsPanel.requestThreadPage()
        }

        function stopThreadPaging() {
            smsPanel.threadPagingThreadId = ""
            threadPageQuietTimer.stop()
            threadPageCapTimer.stop()
            smsPanel.flushOpenThreadMessages()
        }

        function requestThreadPage() {
            const threadId = smsPanel.threadPagingThreadId
            if (threadId === "" || threadId !== smsPanel.openThreadId) return
            if (smsPanel.threadFetchPageIndex >= smsPanel.threadFetchMaxPages) {
                smsPanel.stopThreadPaging()
                return
            }
            smsPanel.threadFetchPrevCount = Object.keys(smsPanel.openThreadMessages).length
            const start = smsPanel.threadFetchPageIndex * smsPanel.threadFetchPageSize
            threadFetchProcess.command = ["kdeconnect-sms-dbus", "thread", smsPanel.deviceId,
                threadId, String(smsPanel.threadFetchPageSize), String(start)]
            threadFetchProcess.running = true
            // The page is done when its signals go quiet (300ms) or the
            // 2s cap hits, whichever comes first -- see the timers below.
            threadPageQuietTimer.restart()
            threadPageCapTimer.restart()
        }

        // conversationLoaded only fires when the daemon actually pulled a
        // fresh batch from the phone; a page fully served from
        // kdeconnectd's own cache never emits it, so it only restarts the
        // quiet timer like any other signal for this thread. The quiet
        // timer (no signals for 300ms) and the 2s cap below are what
        // guarantee a page is evaluated either way.
        function onThreadPageLoadedSignal(threadId) {
            if (smsPanel.threadPagingThreadId === "" || String(threadId) !== smsPanel.threadPagingThreadId) return
            threadPageQuietTimer.restart()
        }

        function evaluateThreadPage() {
            threadPageQuietTimer.stop()
            threadPageCapTimer.stop()
            smsPanel.flushOpenThreadMessages()
            const threadId = smsPanel.threadPagingThreadId
            if (threadId === "" || threadId !== smsPanel.openThreadId) return
            const newCount = Object.keys(smsPanel.openThreadMessages).length
            if (newCount <= smsPanel.threadFetchPrevCount) {
                // No new uids landed (wedged phone, or history exhausted).
                // Whatever already arrived stays on screen either way.
                smsPanel.stopThreadPaging()
                return
            }
            smsPanel.threadFetchPageIndex += 1
            smsPanel.requestThreadPage()
        }

        function startWatcherIfNeeded() {
            if (watchProcess.running) return
            watchProcess.command = ["kdeconnect-sms-dbus", "watch", smsPanel.deviceId]
            watchProcess.running = true
        }

        function sendCurrentMessage() {
            const text = composeInput.text.trim()
            if (text.length === 0 || smsPanel.openThreadId === "" || smsPanel.sendPending) return
            smsPanel.sendError = ""
            smsPanel.sendPending = true
            sendProcess.command = ["kdeconnect-sms-dbus", "send", smsPanel.deviceId, smsPanel.openThreadId, text]
            sendProcess.running = true
            composeInput.text = ""
        }

        // Re-checks device/daemon state; on a fresh transition into "ok"
        // this (re)fetches the conversation list/contacts and (re)starts
        // the signal watcher, so a cold start or a device reconnect both
        // self-heal without re-fetching anything that's already cached.
        function refreshStatus() {
            statusProcess.running = true
        }

        function manualRefresh(fullKick) {
            smsPanel.refreshStatus()
            if (smsPanel.deviceState === "ok" && smsPanel.deviceId !== "") {
                // Cached by default: read the daemon's local cache only.
                // The watch stream already delivers live updates, so a
                // full refresh on every open would just re-burst every
                // thread. The refresh button passes fullKick=true to
                // force a re-sync from the phone instead.
                conversationsProcess.command = fullKick
                    ? ["kdeconnect-sms-dbus", "conversations", smsPanel.deviceId]
                    : ["kdeconnect-sms-dbus", "conversations", smsPanel.deviceId, "cached"]
                conversationsProcess.running = true
                contactsProcess.command = ["kdeconnect-sms-dbus", "contacts", smsPanel.deviceId]
                contactsProcess.running = true
            }
        }

        function statusLineText() {
            switch (smsPanel.deviceState) {
                case "no-daemon": return "KDE Connect isn't running."
                case "no-device": return "No KDE Connect device found."
                case "not-paired": return "Pair " + (smsPanel.deviceName || "your phone") + " in KDE Connect first."
                case "unreachable": return (smsPanel.deviceName || "Device") + " is unreachable."
                case "sms-disabled": return "SMS plugin isn't enabled on " + (smsPanel.deviceName || "your phone") + "."
                case "": return "Checking…"
                default: return "Unknown status."
            }
        }

        WlrLayershell.layer: WlrLayer.Top
        WlrLayershell.keyboardFocus: WlrKeyboardFocus.OnDemand
        WlrLayershell.namespace: "sms-panel"

        anchors.top: true
        anchors.bottom: true
        anchors.right: true

        implicitWidth: 380
        margins.top: 0

        color: root.colBg
        visible: false

        // SMS state (status/conversations/contacts/watcher) is preloaded
        // once in Component.onCompleted below and kept alive for the life
        // of the shell -- visibility only toggles the slow status-poll
        // timer and kicks a non-destructive background refresh so opening
        // the panel is instant instead of re-running the full init.
        onVisibleChanged: {
            if (visible) {
                smsPanel.statusError = ""
                smsPanel.manualRefresh()
                smsStatusTimer.running = true
            } else {
                smsStatusTimer.running = false
            }
        }

        Component.onCompleted: smsPanel.refreshStatus()

        Timer {
            id: smsStatusTimer
            // Message delivery itself is purely signal-driven (watchProcess
            // below); this is only a slow fallback to notice the phone
            // going unreachable or being re-paired while the panel is open.
            interval: 30000
            repeat: true
            running: false
            onTriggered: smsPanel.refreshStatus()
        }

        // Paged-history fetch timers (see startThreadPaging/requestThreadPage
        // above): threadPageQuietTimer fires 300ms after the last signal
        // for the thread being paged, so a page is evaluated as soon as
        // its burst lands instead of waiting out a fixed timeout;
        // threadPageCapTimer guarantees evaluation within 2s even if
        // signals keep flowing or the phone never answers. The old design
        // waited a fixed 5s per page whenever conversationLoaded never
        // fired (every cache-served page), so 6 pages took up to 30s.
        Timer {
            id: threadPageQuietTimer
            interval: 300
            repeat: false
            onTriggered: smsPanel.evaluateThreadPage()
        }

        Timer {
            id: threadPageCapTimer
            interval: 2000
            repeat: false
            onTriggered: smsPanel.evaluateThreadPage()
        }

        // Coalescing timers: watch-event bursts rebuild the conversation
        // list / message list once per flush instead of once per signal.
        Timer {
            id: threadListFlushTimer
            interval: 100
            repeat: false
            onTriggered: smsPanel.flushThreadSummaries()
        }

        Timer {
            id: openThreadFlushTimer
            interval: 120
            repeat: false
            onTriggered: smsPanel.flushOpenThreadMessages()
        }

        Process {
            id: statusProcess

            command: ["kdeconnect-sms-dbus", "status"]

            stdout: StdioCollector { id: statusStdout }
            stderr: StdioCollector { id: statusStderr }

            onExited: (exitCode) => {
                if (exitCode !== 0) {
                    smsPanel.deviceState = "no-daemon"
                    smsPanel.deviceId = ""
                    smsPanel.statusError = statusStderr.text.trim().split("\n")[0]
                    watchProcess.running = false
                    return
                }
                try {
                    const parsed = JSON.parse(statusStdout.text)
                    const wasOk = smsPanel.deviceState === "ok" && smsPanel.deviceId !== ""

                    smsPanel.deviceState = parsed.state || "no-daemon"
                    smsPanel.deviceId = parsed.deviceId || ""
                    smsPanel.deviceName = parsed.deviceName || ""
                    smsPanel.statusError = ""

                    const nowOk = smsPanel.deviceState === "ok" && smsPanel.deviceId !== ""

                    if (nowOk && !wasOk) {
                        smsPanel.threads = {}
                        smsPanel.threadIdList = []
                        conversationsProcess.command = ["kdeconnect-sms-dbus", "conversations", smsPanel.deviceId]
                        conversationsProcess.running = true
                        contactsProcess.command = ["kdeconnect-sms-dbus", "contacts", smsPanel.deviceId]
                        contactsProcess.running = true
                        smsPanel.startWatcherIfNeeded()
                    } else if (!nowOk && wasOk) {
                        watchProcess.running = false
                        smsPanel.closeThreadView()
                    } else if (nowOk) {
                        smsPanel.startWatcherIfNeeded()
                    }
                } catch (error) {
                    smsPanel.deviceState = "no-daemon"
                    smsPanel.statusError = "Failed to parse status"
                }
            }
        }

        Process {
            id: conversationsProcess

            stdout: StdioCollector { id: conversationsStdout }
            stderr: StdioCollector { id: conversationsStderr }

            onExited: (exitCode) => {
                if (exitCode !== 0) {
                    const firstLine = conversationsStderr.text.trim().split("\n")[0]
                    smsPanel.conversationsError = firstLine || "conversations failed"
                    return
                }
                try {
                    const list = JSON.parse(conversationsStdout.text)
                    smsPanel.conversationsError = ""
                    smsPanel.mergeThreadSummaries(list)
                } catch (error) {
                    smsPanel.conversationsError = "Failed to parse conversations"
                }
            }
        }

        Process {
            id: contactsProcess

            stdout: StdioCollector { id: contactsStdout }

            onExited: (exitCode) => {
                if (exitCode !== 0) return
                try {
                    const list = JSON.parse(contactsStdout.text)
                    const map = {}
                    for (const entry of list) map[entry.number] = entry.name
                    smsPanel.contactNames = map
                    smsPanel.contactList = Object.keys(map).map(number => ({number: number, name: map[number]}))
                        .sort((a, b) => a.name.localeCompare(b.name))
                } catch (error) {
                    // Best-effort only -- rows just fall back to raw numbers.
                }
            }
        }

        // Fire-and-forget: asks kdeconnectd to (re)fill its local cache for
        // this thread. The actual messages arrive as conversationUpdated
        // events on watchProcess below, same as live-incoming messages.
        Process {
            id: threadFetchProcess

            stderr: StdioCollector { id: threadFetchStderr }

            onExited: (exitCode) => {
                if (exitCode !== 0) {
                    smsPanel.sendError = threadFetchStderr.text.trim().split("\n")[0] || "Failed to load thread"
                }
            }
        }

        Process {
            id: sendProcess

            stderr: StdioCollector { id: sendStderr }

            onExited: (exitCode) => {
                smsPanel.sendPending = false
                if (exitCode !== 0) {
                    smsPanel.sendError = sendStderr.text.trim().split("\n")[0] || "Send failed"
                    smsPanel.sendPendingNew = false
                } else if (smsPanel.sendPendingNew) {
                    // New thread: the phone confirms it via the live push
                    // stream; head back to the list and let it arrive.
                    smsPanel.closeNewMessage()
                    smsPanel.manualRefresh(false)
                }
            }
        }

        // Serial attachment downloader: one request at a time; the file
        // itself arrives via the attachmentReceived signal on watchProcess.
        Process {
            id: attachmentProcess

            onExited: smsPanel.pumpAttachmentQueue()
        }

        // Long-lived for the whole shell session (started once at preload,
        // see Component.onCompleted above): one JSON object per line,
        // decoded straight from kdeconnectd's D-Bus signals by
        // kdeconnect-sms-dbus (no polling -- see its `watch` subcommand).
        Process {
            id: watchProcess

            stdout: SplitParser {
                splitMarker: "\n"

                onRead: (line) => {
                    if (!line || line.trim().length === 0) return
                    try {
                        const evt = JSON.parse(line)
                        if (evt.signal === "attachmentReceived") {
                            if (!evt.fileName) return
                            const next = Object.assign({}, smsPanel.attachmentFiles)
                            next[evt.fileName] = evt.filePath
                            smsPanel.attachmentFiles = next
                        } else if (evt.signal === "conversationCreated" || evt.signal === "conversationUpdated") {
                            // A malformed/empty signal missing either id must
                            // not create a phantom message; a real MMS with
                            // an empty body but a valid uid still renders.
                            if (evt.threadId === undefined || evt.threadId === null
                                || evt.uid === undefined || evt.uid === null) return
                            smsPanel.mergeThreadSummary(evt)
                            smsPanel.mergeOpenMessage(evt)
                            // Signals for the thread being paged push the
                            // page's quiet timer out; other threads' signals
                            // must not delay this page's evaluation.
                            if (smsPanel.threadPagingThreadId !== ""
                                && String(evt.threadId) === smsPanel.threadPagingThreadId)
                                threadPageQuietTimer.restart()
                        } else if (evt.signal === "conversationLoaded") {
                            if (evt.threadId === undefined || evt.threadId === null) return
                            smsPanel.onThreadPageLoadedSignal(evt.threadId)
                        } else if (evt.signal === "conversationRemoved") {
                            smsPanel.removeThread(evt.threadId)
                        }
                    } catch (error) {
                        // Ignore a malformed/partial line; the stream continues.
                    }
                }
            }
        }

        Component {
            id: threadRowComponent

            Rectangle {
                id: threadRow

                required property var modelData // threadId as a string

                readonly property var msg: smsPanel.threads[threadRow.modelData]
                readonly property bool unread: threadRow.msg
                    && threadRow.msg.read === 0 && threadRow.msg.type === 1

                width: parent.width
                height: threadRowLayout.implicitHeight + 10

                radius: 6
                color: threadRowArea.containsMouse
                    ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.15)
                    : "transparent"

                MouseArea {
                    id: threadRowArea

                    anchors.fill: parent
                    hoverEnabled: true

                    onClicked: smsPanel.openThreadView(threadRow.modelData)

                    ColumnLayout {
                        id: threadRowLayout

                        anchors.left: parent.left
                        anchors.right: parent.right
                        anchors.verticalCenter: parent.verticalCenter
                        anchors.leftMargin: 6
                        anchors.rightMargin: 6

                        spacing: 2

                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 6

                            Rectangle {
                                visible: threadRow.unread
                                width: 6
                                height: 6
                                radius: 3
                                color: root.colAccent
                            }

                            Text {
                                Layout.fillWidth: true

                                text: threadRow.msg ? smsPanel.displayNameFor(threadRow.msg.addresses) : threadRow.modelData
                                elide: Text.ElideRight

                                color: root.colFg

                                font {
                                    family: root.fontFamily
                                    pixelSize: root.fontSize - 1
                                    bold: threadRow.unread
                                }
                            }
                        }

                        Text {
                            Layout.fillWidth: true

                            text: threadRow.msg
                                ? (threadRow.msg.body !== ""
                                    ? threadRow.msg.body
                                    : ((threadRow.msg.attachments || []).length > 0 ? "Image" : ""))
                                : ""
                            elide: Text.ElideRight

                            color: root.colMuted

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize - 2
                            }
                        }
                    }
                }
            }
        }

        Component {
            id: messageBubbleComponent

            Item {
                id: bubbleItem

                required property var modelData // a decoded ConversationMessage

                // Types.MessageTypeSent/Draft/Outbox/Failed/Queued, per
                // ConversationMessage::isOutgoing() -- models/conversationmessage.cpp
                readonly property bool outgoing: [2, 3, 4, 5, 6].includes(bubbleItem.modelData.type)
                readonly property bool hasAttachments: (bubbleItem.modelData.attachments || []).length > 0
                readonly property string statusNote: bubbleItem.modelData.type === 5
                    ? "Failed"
                    : (bubbleItem.modelData.type === 4 || bubbleItem.modelData.type === 6)
                        ? "Sending…" : ""

                width: parent.width
                height: bubbleColumn.implicitHeight + 10

                Column {
                    id: bubbleColumn

                    anchors.right: bubbleItem.outgoing ? parent.right : undefined
                    anchors.left: bubbleItem.outgoing ? undefined : parent.left
                    width: Math.max(Math.min(parent.width * 0.78, bubbleInner.implicitWidth + 20),
                        bubbleItem.hasAttachments ? Math.min(260, parent.width * 0.7) : 0)
                    spacing: 2

                    Repeater {
                        model: bubbleItem.modelData.attachments || []

                        delegate: Item {
                            id: attItem

                            required property var modelData // {partId, mime, uniqueId}

                            readonly property string localPath: smsPanel.attachmentFiles[attItem.modelData.uniqueId] || ""
                            readonly property bool isImage: attItem.modelData.mime.indexOf("image/") === 0

                            width: bubbleColumn.width
                            height: attItem.localPath !== "" && attItem.isImage ? 200 : 40

                            Component.onCompleted: smsPanel.requestAttachment(attItem.modelData.partId, attItem.modelData.uniqueId)

                            Rectangle {
                                anchors.fill: parent
                                radius: 10
                                color: Qt.rgba(root.colFg.r, root.colFg.g, root.colFg.b, 0.08)

                                Text {
                                    anchors.centerIn: parent
                                    visible: attItem.localPath === "" || !attItem.isImage || attImage.status === Image.Error
                                    text: attItem.localPath === ""
                                        ? "Loading attachment…"
                                        : attImage.status === Image.Error
                                            ? "Couldn't preview this image"
                                            : attItem.modelData.mime
                                    color: root.colMuted

                                    font {
                                        family: root.fontFamily
                                        pixelSize: root.fontSize - 2
                                    }
                                }
                            }

                            Image {
                                id: attImage

                                anchors.fill: parent
                                visible: attItem.localPath !== "" && attItem.isImage && attImage.status !== Image.Error
                                source: attItem.localPath !== "" ? "file://" + attItem.localPath : ""
                                fillMode: Image.PreserveAspectFit
                                asynchronous: true
                                cache: false
                            }

                            MouseArea {
                                anchors.fill: parent
                                enabled: attItem.localPath !== ""
                                cursorShape: Qt.PointingHandCursor
                                onClicked: Qt.openUrlExternally("file://" + attItem.localPath)
                            }
                        }
                    }

                    Rectangle {
                        visible: bubbleItem.modelData.body !== ""
                        width: parent.width
                        height: bubbleInner.implicitHeight + 16
                        radius: 10

                        color: bubbleItem.outgoing
                            ? root.colAccent
                            : Qt.rgba(root.colFg.r, root.colFg.g, root.colFg.b, 0.08)

                        Text {
                            id: bubbleInner

                            anchors.fill: parent
                            anchors.margins: 8

                            text: bubbleItem.modelData.body
                            wrapMode: Text.Wrap

                            color: bubbleItem.outgoing ? root.colBg : root.colFg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize - 1
                            }
                        }
                    }

                    Text {
                        anchors.right: bubbleItem.outgoing ? parent.right : undefined

                        text: bubbleItem.statusNote.length > 0
                            ? bubbleItem.statusNote
                            : Qt.formatDateTime(new Date(bubbleItem.modelData.date), "h:mm AP")

                        color: bubbleItem.modelData.type === 5 ? root.colRed : root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize - 3
                        }
                    }
                }
            }
        }

        Component {
            id: contactRowComponent

            Rectangle {
                id: contactRow

                required property var modelData // {number, name}

                width: parent.width
                height: 38

                radius: 6
                color: contactRowArea.containsMouse
                    ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.15)
                    : "transparent"

                MouseArea {
                    id: contactRowArea

                    anchors.fill: parent
                    hoverEnabled: true

                    onClicked: smsPanel.newMessageContact = contactRow.modelData

                    ColumnLayout {
                        anchors.left: parent.left
                        anchors.right: parent.right
                        anchors.verticalCenter: parent.verticalCenter
                        anchors.leftMargin: 6
                        anchors.rightMargin: 6

                        spacing: 1

                        Text {
                            Layout.fillWidth: true
                            text: contactRow.modelData.name
                            elide: Text.ElideRight
                            color: root.colFg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize - 1
                            }
                        }

                        Text {
                            Layout.fillWidth: true
                            text: contactRow.modelData.number
                            elide: Text.ElideRight
                            color: root.colMuted

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize - 2
                            }
                        }
                    }
                }
            }
        }

        // ---- Chrome: thin left border matching the bar-popup borders ----

        Rectangle {
            anchors.left: parent.left
            anchors.top: parent.top
            anchors.bottom: parent.bottom
            width: 1
            color: root.colMuted
            opacity: 0.4
        }

        ColumnLayout {
            id: smsPanelLayout

            anchors.fill: parent
            anchors.margins: 8

            spacing: 8

            RowLayout {
                Layout.fillWidth: true
                spacing: 8

                MouseArea {
                    id: smsBackArea

                    visible: smsPanel.openThreadId !== "" || smsPanel.smsNewMessage
                    implicitWidth: smsBackText.implicitWidth
                    implicitHeight: smsBackText.implicitHeight
                    hoverEnabled: true

                    onClicked: smsPanel.goBack()

                    Text {
                        id: smsBackText
                        text: "‹"
                        color: smsBackArea.containsMouse ? root.colFg : root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize
                            bold: true
                        }
                    }
                }

                Text {
                    Layout.fillWidth: true

                    text: smsPanel.smsNewMessage
                        ? "New message"
                        : smsPanel.openThreadId === ""
                            ? "Messages"
                            : smsPanel.displayNameFor(
                                smsPanel.threads[smsPanel.openThreadId]
                                    ? smsPanel.threads[smsPanel.openThreadId].addresses : [])
                    elide: Text.ElideRight

                    color: root.colFg

                    font {
                        family: root.fontFamily
                        pixelSize: root.fontSize
                        bold: true
                    }
                }

                Text {
                    Layout.fillWidth: true

                    visible: smsPanel.conversationsError.length > 0 && smsPanel.openThreadId === ""
                    elide: Text.ElideRight

                    text: smsPanel.conversationsError
                    color: root.colRed

                    font {
                        family: root.fontFamily
                        pixelSize: root.fontSize - 2
                    }
                }

                MouseArea {
                    id: smsRefreshArea

                    visible: smsPanel.openThreadId === "" && !smsPanel.smsNewMessage
                    implicitWidth: smsRefreshText.implicitWidth
                    implicitHeight: smsRefreshText.implicitHeight
                    hoverEnabled: true

                    onClicked: smsPanel.manualRefresh(true)

                    Text {
                        id: smsRefreshText

                        text: "⟳"
                        color: smsRefreshArea.containsMouse ? root.colFg : root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize
                            bold: true
                        }
                    }
                }

                MouseArea {
                    id: smsNewArea

                    visible: smsPanel.openThreadId === "" && !smsPanel.smsNewMessage
                    implicitWidth: smsNewText.implicitWidth
                    implicitHeight: smsNewText.implicitHeight
                    hoverEnabled: true

                    onClicked: smsPanel.openNewMessage()

                    Text {
                        id: smsNewText

                        text: "+"
                        color: smsNewArea.containsMouse ? root.colFg : root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize
                            bold: true
                        }
                    }
                }
            }

            Text {
                Layout.fillWidth: true

                visible: smsPanel.deviceState !== "ok"
                elide: Text.ElideRight

                text: smsPanel.statusError.length > 0 ? smsPanel.statusError : smsPanel.statusLineText()
                color: root.colMuted

                font {
                    family: root.fontFamily
                    pixelSize: root.fontSize - 1
                }
            }

            Rectangle {
                Layout.fillWidth: true
                height: 30

                visible: smsPanel.deviceState === "ok" && smsPanel.openThreadId === "" && !smsPanel.smsNewMessage

                radius: 6
                color: Qt.rgba(root.colFg.r, root.colFg.g, root.colFg.b, 0.08)
                border.color: root.colMuted
                border.width: 1

                Text {
                    anchors.fill: parent
                    anchors.leftMargin: 8
                    verticalAlignment: Text.AlignVCenter

                    visible: threadSearchInput.text === ""
                    text: "Search conversations"
                    color: root.colMuted

                    font {
                        family: root.fontFamily
                        pixelSize: root.fontSize - 1
                    }
                }

                TextInput {
                    id: threadSearchInput

                    anchors.fill: parent
                    anchors.leftMargin: 8
                    anchors.rightMargin: 8
                    verticalAlignment: TextInput.AlignVCenter

                    color: root.colFg

                    font {
                        family: root.fontFamily
                        pixelSize: root.fontSize - 1
                    }

                    onTextChanged: smsPanel.threadFilter = text
                    Keys.onEscapePressed: { text = ""; smsPanel.threadFilter = "" }
                }
            }

            Flickable {
                id: smsListFlick

                visible: smsPanel.deviceState === "ok" && smsPanel.openThreadId === "" && !smsPanel.smsNewMessage

                Layout.fillWidth: true
                Layout.fillHeight: true

                contentWidth: width
                contentHeight: smsListContent.implicitHeight
                clip: true

                Column {
                    id: smsListContent

                    width: smsListFlick.width
                    spacing: 4

                    Text {
                        visible: smsPanel.visibleThreadIds().length === 0
                        text: smsPanel.threadFilter !== "" ? "No matching conversations." : "No conversations yet."
                        color: root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize
                        }
                    }

                    Repeater {
                        model: smsPanel.visibleThreadIds()
                        delegate: threadRowComponent
                    }
                }
            }

            Flickable {
                id: smsThreadFlick

                visible: smsPanel.openThreadId !== ""

                Layout.fillWidth: true
                Layout.fillHeight: true

                contentWidth: width
                contentHeight: smsThreadContent.implicitHeight
                clip: true

                // Snap to the newest messages only while the view is
                // already near the bottom; once the user scrolls up to
                // read history, paging growth leaves the view alone.
                onContentYChanged: smsPanel.smsThreadNearBottom =
                    smsThreadFlick.contentY >= smsThreadFlick.contentHeight - smsThreadFlick.height - 120
                onContentHeightChanged: {
                    if (smsPanel.smsThreadNearBottom)
                        smsThreadFlick.contentY = Math.max(0, smsThreadFlick.contentHeight - smsThreadFlick.height)
                }

                Column {
                    id: smsThreadContent

                    width: smsThreadFlick.width
                    spacing: 4

                    Repeater {
                        model: smsPanel.sortedOpenMessages()
                        delegate: messageBubbleComponent
                    }
                }
            }

            ColumnLayout {
                visible: smsPanel.smsNewMessage && !smsPanel.newMessageContact

                Layout.fillWidth: true
                Layout.fillHeight: true
                spacing: 8

                onVisibleChanged: if (visible) newMessageSearchInput.forceActiveFocus()

                Rectangle {
                    Layout.fillWidth: true
                    height: 30

                    radius: 6
                    color: Qt.rgba(root.colFg.r, root.colFg.g, root.colFg.b, 0.08)
                    border.color: root.colMuted
                    border.width: 1

                    Text {
                        anchors.fill: parent
                        anchors.leftMargin: 8
                        verticalAlignment: Text.AlignVCenter

                        visible: newMessageSearchInput.text === ""
                        text: "Search contacts or type a number"
                        color: root.colMuted

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize - 1
                        }
                    }

                    TextInput {
                        id: newMessageSearchInput

                        anchors.fill: parent
                        anchors.leftMargin: 8
                        anchors.rightMargin: 8
                        verticalAlignment: TextInput.AlignVCenter

                        color: root.colFg

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize - 1
                        }

                        onTextChanged: smsPanel.newMessageSearch = text
                        Keys.onEscapePressed: smsPanel.goBack()
                    }
                }

                Flickable {
                    Layout.fillWidth: true
                    Layout.fillHeight: true

                    contentWidth: width
                    contentHeight: newContactContent.implicitHeight
                    clip: true

                    Column {
                        id: newContactContent

                        width: parent.width
                        spacing: 4

                        Rectangle {
                            width: parent.width
                            height: 34

                            visible: smsPanel.newMessageRawNumber() !== ""

                            radius: 6
                            color: rawNumberArea.containsMouse
                                ? Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.15)
                                : "transparent"

                            MouseArea {
                                id: rawNumberArea

                                anchors.fill: parent
                                hoverEnabled: true

                                onClicked: smsPanel.newMessageContact = {number: smsPanel.newMessageRawNumber(), name: smsPanel.newMessageRawNumber()}

                                Text {
                                    anchors.left: parent.left
                                    anchors.verticalCenter: parent.verticalCenter
                                    anchors.leftMargin: 6

                                    text: "Text " + smsPanel.newMessageRawNumber()
                                    color: root.colFg

                                    font {
                                        family: root.fontFamily
                                        pixelSize: root.fontSize - 1
                                    }
                                }
                            }
                        }

                        Repeater {
                            model: smsPanel.filteredContacts()
                            delegate: contactRowComponent
                        }
                    }
                }
            }

            ColumnLayout {
                visible: smsPanel.smsNewMessage && smsPanel.newMessageContact !== null

                Layout.fillWidth: true
                Layout.fillHeight: true
                spacing: 8

                onVisibleChanged: if (visible) newMessageInput.forceActiveFocus()

                Text {
                    Layout.fillWidth: true

                    elide: Text.ElideRight
                    text: smsPanel.newMessageContact ? "To: " + smsPanel.newMessageContact.name : ""
                    color: root.colMuted

                    font {
                        family: root.fontFamily
                        pixelSize: root.fontSize - 1
                    }
                }

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 6

                    Rectangle {
                        Layout.fillWidth: true
                        height: 30

                        radius: 6
                        color: Qt.rgba(root.colFg.r, root.colFg.g, root.colFg.b, 0.08)
                        border.color: root.colMuted
                        border.width: 1

                        TextInput {
                            id: newMessageInput

                            anchors.fill: parent
                            anchors.leftMargin: 8
                            anchors.rightMargin: 8
                            verticalAlignment: TextInput.AlignVCenter

                            enabled: !smsPanel.sendPendingNew
                            color: root.colFg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize - 1
                            }

                            onTextChanged: smsPanel.newMessageText = text
                            onAccepted: smsPanel.sendNewMessage()
                            Keys.onEscapePressed: smsPanel.goBack()
                        }
                    }

                    MouseArea {
                        id: smsNewSendArea

                        enabled: !smsPanel.sendPendingNew && smsPanel.newMessageText.trim() !== ""
                        implicitWidth: smsNewSendText.implicitWidth + 12
                        implicitHeight: 30
                        hoverEnabled: true

                        onClicked: smsPanel.sendNewMessage()

                        Rectangle {
                            anchors.fill: parent
                            radius: 6
                            color: smsNewSendArea.containsMouse
                                ? root.colAccent
                                : Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.7)
                        }

                        Text {
                            id: smsNewSendText
                            anchors.centerIn: parent
                            text: "Send"
                            color: root.colBg

                            font {
                                family: root.fontFamily
                                pixelSize: root.fontSize - 1
                                bold: true
                            }
                        }
                    }
                }

                Item {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                }
            }

            RowLayout {
                Layout.fillWidth: true
                visible: smsPanel.openThreadId !== ""
                spacing: 6

                Rectangle {
                    Layout.fillWidth: true
                    height: 30

                    radius: 6
                    color: Qt.rgba(root.colFg.r, root.colFg.g, root.colFg.b, 0.08)
                    border.color: root.colMuted
                    border.width: 1

                    TextInput {
                        id: composeInput

                        anchors.fill: parent
                        anchors.leftMargin: 8
                        anchors.rightMargin: 8
                        verticalAlignment: TextInput.AlignVCenter

                        enabled: !smsPanel.sendPending
                        color: root.colFg

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize - 1
                        }

                        onAccepted: smsPanel.sendCurrentMessage()
                        Keys.onEscapePressed: smsPanel.closeThreadView()
                    }
                }

                MouseArea {
                    id: smsSendArea

                    enabled: !smsPanel.sendPending
                    implicitWidth: smsSendText.implicitWidth + 12
                    implicitHeight: 30
                    hoverEnabled: true

                    onClicked: smsPanel.sendCurrentMessage()

                    Rectangle {
                        anchors.fill: parent
                        radius: 6
                        color: smsSendArea.containsMouse
                            ? root.colAccent
                            : Qt.rgba(root.colAccent.r, root.colAccent.g, root.colAccent.b, 0.7)
                    }

                    Text {
                        id: smsSendText
                        anchors.centerIn: parent
                        text: "Send"
                        color: root.colBg

                        font {
                            family: root.fontFamily
                            pixelSize: root.fontSize - 1
                            bold: true
                        }
                    }
                }
            }

            Text {
                Layout.fillWidth: true
                visible: smsPanel.sendError.length > 0
                elide: Text.ElideRight
                text: smsPanel.sendError
                color: root.colRed

                font {
                    family: root.fontFamily
                    pixelSize: root.fontSize - 2
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Layout
    // ─────────────────────────────────────────────

    RowLayout {
        anchors.fill: parent

        anchors.leftMargin: 10
        anchors.rightMargin: 10

        spacing: 8

        // ─────────────────────────────────────────
        // Time
        // ─────────────────────────────────────────

        MouseArea {
            Layout.preferredWidth: clockText.implicitWidth
            Layout.preferredHeight: clockText.implicitHeight

            acceptedButtons: Qt.NoButton

            onWheel: {
                root.showFullDate = !root.showFullDate
            }

            Text {
                id: clockText

                color: root.colAccent

                font {
                    family: root.fontFamily
                    pixelSize: root.fontSize
                    bold: true
                }

                text: root.showFullDate
                    ? Qt.formatDateTime(
                        new Date(),
                        "dddd, MMMM dd - h:mm AP"
                    )
                    : Qt.formatDateTime(
                        new Date(),
                        "h:mm AP"
                    )

                Timer {
                    interval: 1000
                    running: true
                    repeat: true

                    onTriggered: {
                        clockText.text = root.showFullDate
                            ? Qt.formatDateTime(
                                new Date(),
                                "dddd, MMMM dd - h:mm AP"
                            )
                            : Qt.formatDateTime(
                                new Date(),
                                "h:mm AP"
                            )
                    }
                }
            }
        }

        Rectangle {
            width: 1
            height: 16

            color: root.colMuted
            visible: root.weatherTemp !== ""
        }

        // ─────────────────────────────────────────
        // Weather
        // ─────────────────────────────────────────

        MouseArea {
            Layout.preferredWidth: weatherText.implicitWidth
            Layout.preferredHeight: weatherText.implicitHeight

            visible: root.weatherTemp !== ""

            acceptedButtons: Qt.RightButton

            onClicked: {
                root.weatherDisplayMode = root.cycleDisplayMode(root.weatherDisplayMode)
            }

            Text {
                id: weatherText

                text: {
                    if (root.weatherDisplayMode === 0) return root.weatherIcon
                    if (root.weatherDisplayMode === 1) return root.weatherTemp
                    return root.weatherIcon + " " + root.weatherTemp
                }

                color: root.colAccent

                font {
                    family: root.fontFamily
                    pixelSize: root.fontSize
                    bold: true
                }
            }
        }

        // Push right side to the right
        Item {
            Layout.fillWidth: true
        }

        // ─────────────────────────────────────────
        // Wi-Fi / IP Address
        // ─────────────────────────────────────────

        MouseArea {
            id: wifiArea

            Layout.preferredWidth: wifiText.implicitWidth
            Layout.preferredHeight: wifiText.implicitHeight

            acceptedButtons: Qt.LeftButton | Qt.RightButton

            onClicked: (mouse) => {
                if (mouse.button === Qt.RightButton) {
                    root.wifiDisplayMode = root.cycleDisplayMode(root.wifiDisplayMode)
                } else {
                    root.togglePopup(wifiPopup)
                }
            }

            onWheel: {
                root.wifiScrollMode = (root.wifiScrollMode + 1) % 3
            }

            Text {
                id: wifiText

                text: {
                    if (root.wifiScrollMode === 2) {
                        return "󰇚 " + root.formatSpeed(root.wifiDownSpeed)
                            + "  󰕒 " + root.formatSpeed(root.wifiUpSpeed)
                    }

                    const net = root.connectedWifiNetwork
                    const icon = root.wifiScrollMode === 1
                        ? "󰩟"
                        : (root.vpnActive ? "󰑣" : root.wifiIconFor(net))
                    const label = root.wifiScrollMode === 1
                        ? root.ipAddress
                        : (net ? net.name : "Disconnected")

                    if (root.wifiDisplayMode === 0) return icon
                    if (root.wifiDisplayMode === 1) return label
                    return icon + " " + label
                }

                color: root.colAccent

                font {
                    family: root.fontFamily
                    pixelSize: root.fontSize
                    bold: true
                }
            }
        }

        Rectangle {
            width: 1
            height: 16

            color: root.colMuted
        }

        // ─────────────────────────────────────────
        // Bluetooth
        // ─────────────────────────────────────────

        MouseArea {
            id: bluetoothArea

            Layout.preferredWidth: bluetoothText.implicitWidth
            Layout.preferredHeight: bluetoothText.implicitHeight

            acceptedButtons: Qt.LeftButton | Qt.RightButton

            onClicked: (mouse) => {
                if (mouse.button === Qt.RightButton) {
                    root.bluetoothDisplayMode = root.cycleDisplayMode(root.bluetoothDisplayMode)
                } else {
                    root.togglePopup(bluetoothPopup)
                }
            }

            Text {
                id: bluetoothText

                text: {
                    const adapter = Bluetooth.defaultAdapter
                    const enabled = adapter && adapter.enabled
                    const connectedCount = enabled
                        ? adapter.devices.values.filter((d) => d.connected).length
                        : 0

                    const icon = enabled ? "󰂯" : "󰂲"
                    const label = !enabled
                        ? "Off"
                        : (connectedCount > 0 ? connectedCount + " connected" : "On")

                    if (root.bluetoothDisplayMode === 0) return icon
                    if (root.bluetoothDisplayMode === 1) return label
                    return icon + " " + label
                }

                color: root.colAccent

                font {
                    family: root.fontFamily
                    pixelSize: root.fontSize
                    bold: true
                }
            }
        }

        Rectangle {
            width: 1
            height: 16

            color: root.colMuted
        }

        // ─────────────────────────────────────────
        // Volume
        // ─────────────────────────────────────────

        MouseArea {
            id: volumeArea

            Layout.preferredWidth: volumeText.implicitWidth
            Layout.preferredHeight: volumeText.implicitHeight

            acceptedButtons: Qt.LeftButton | Qt.RightButton

            onClicked: (mouse) => {
                if (mouse.button === Qt.RightButton) {
                    root.volumeDisplayMode = root.cycleDisplayMode(root.volumeDisplayMode)
                } else {
                    root.togglePopup(audioPopup)
                }
            }

            onWheel: (wheel) => {
                volumeChangeProcess.command = [
                    "swayosd-client",
                    "--output-volume",
                    wheel.angleDelta.y > 0 ? "+5" : "-5"
                ]

                volumeChangeProcess.running = true
                volumeProcess.running = true
            }

            Text {
                id: volumeText

                text: {
                    if (root.volumeDisplayMode === 0) return "󰕾"
                    if (root.volumeDisplayMode === 1) return root.volumePercent + "%"
                    return "󰕾 " + root.volumePercent + "%"
                }

                color: root.colAccent

                font {
                    family: root.fontFamily
                    pixelSize: root.fontSize
                    bold: true
                }
            }
        }

        // ─────────────────────────────────────────
        // Battery
        // ─────────────────────────────────────────

        Rectangle {
            width: 1
            height: 16

            color: root.colMuted
            visible: root.hasBattery
        }

        Timer {
            id: batteryBlinkTimer

            interval: 500
            repeat: true
            running: root.hasBattery && root.batteryPercent < 5 && !root.batteryCharging

            onRunningChanged: {
                if (!running) {
                    root.batteryBlinkOn = true
                }
            }

            onTriggered: {
                root.batteryBlinkOn = !root.batteryBlinkOn
            }
        }

        MouseArea {
            Layout.preferredWidth: batteryText.implicitWidth
            Layout.preferredHeight: batteryText.implicitHeight

            visible: root.hasBattery

            acceptedButtons: Qt.RightButton

            onClicked: {
                root.batteryDisplayMode = root.cycleDisplayMode(root.batteryDisplayMode)
            }

            Text {
                id: batteryText

                opacity: root.hasBattery && root.batteryPercent < 5 && !root.batteryCharging && !root.batteryBlinkOn
                    ? 0
                    : 1

                text: {
                    let icon
                    if (root.batteryCharging) {
                        icon = "󰂄"
                    } else if (root.batteryPercent >= 90) {
                        icon = "󰁹"
                    } else if (root.batteryPercent >= 80) {
                        icon = "󰂂"
                    } else if (root.batteryPercent >= 70) {
                        icon = "󰂁"
                    } else if (root.batteryPercent >= 60) {
                        icon = "󰂀"
                    } else if (root.batteryPercent >= 50) {
                        icon = "󰁿"
                    } else if (root.batteryPercent >= 40) {
                        icon = "󰁾"
                    } else if (root.batteryPercent >= 30) {
                        icon = "󰁽"
                    } else if (root.batteryPercent >= 20) {
                        icon = "󰁼"
                    } else if (root.batteryPercent >= 10) {
                        icon = "󰁻"
                    } else {
                        icon = "󰂎"
                    }
                    if (root.batteryDisplayMode === 0) return icon
                    const label = root.batteryPercent + "%"
                    if (root.batteryDisplayMode === 1) return label
                    return icon + " " + label
                }

                color: root.batteryPercent <= 20 && !root.batteryCharging
                    ? root.colRed
                    : root.colAccent

                font {
                    family: root.fontFamily
                    pixelSize: root.fontSize
                    bold: true
                }
            }
        }

    }

    // ─────────────────────────────────────────────
    // Focused Window Title (centered)
    // ─────────────────────────────────────────────

    Text {
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.verticalCenter: parent.verticalCenter

        width: Math.min(implicitWidth, 600)

        horizontalAlignment: Text.AlignHCenter

        text: root.focusedWindowTitle

        color: root.colFg

        elide: Text.ElideRight

        font {
            family: root.fontFamily
            pixelSize: root.fontSize
        }
    }

}
