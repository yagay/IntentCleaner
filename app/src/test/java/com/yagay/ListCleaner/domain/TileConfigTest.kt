package com.yagay.ListCleaner.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TileConfigTest {
    @Test fun oldRemoteConfigDefaultsToTileCleaningOff() {
        val old = """{"rules":[],"mode":"HIDE_SELECTED","priorities":{},"diagnostic":false}"""
        assertEquals(TileConfig(), Json.decodeFromString(ModuleConfig.serializer(), old).validated().tiles)
    }
    @Test fun tileSettingsRoundTripWithRulesAndBackup() {
        val tiles = TileConfig(true, setOf("wifi", "custom(com.example/com.example.Tile)"))
        val config = ModuleConfig(emptySet(), DisplayMode.HIDE_SELECTED, PriorityConfig(), false, 10715, tiles)
        assertEquals(config, Json.decodeFromString(ModuleConfig.serializer(), Json.encodeToString(ModuleConfig.serializer(), config)).validated())
        val backup = RuleBackup(version = 4, blacklist = true, rules = emptySet(), displayMode = DisplayMode.HIDE_SELECTED, tiles = tiles)
        assertEquals(backup, Json.decodeFromString(RuleBackup.serializer(), Json.encodeToString(RuleBackup.serializer(), backup)))
    }
    @Test fun malformedAndUnboundedRulesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { TileConfig(true, setOf("bad\nvalue")).validated() }
        assertThrows(IllegalArgumentException::class.java) { TileConfig(true, setOf("custom(com.example/.Tile)")).validated() }
        assertThrows(IllegalArgumentException::class.java) { TileConfig(true, (0..512).map { "tile$it" }.toSet()).validated() }
        assertEquals(TileConfig(), TileConfig().validated())
    }
}
