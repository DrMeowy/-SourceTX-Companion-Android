package com.sourcetx.companion.protocol

import android.content.Context
import org.json.JSONObject

data class BoardTarget(
    val id: String,
    val name: String,
    val chip: String,
    val flashSize: String,
    val flashMode: String,
    val flashFreq: String,
    val psram: String,
    val partitionNvs: String,
    val hardwareId: String?,
    val factoryManifestUrl: String?,
    val factoryManifestSignatureUrl: String?,
    val offlineFactorySha256: String?,
    val enabled: Boolean
)

data class DisplayTarget(
    val id: String,
    val name: String,
    val resolution: String,
    val driver: String,
    val interfaceType: String,
    val touch: String,
    val pins: String,
    val enabled: Boolean
)

data class HardwareCatalog(
    val version: String,
    val companionVersion: String,
    val schemaVersion: Int,
    val boards: List<BoardTarget>,
    val displays: List<DisplayTarget>
)

object TargetsCatalog {
    fun loadFromAssets(context: Context): HardwareCatalog? {
        return try {
            val jsonString = context.assets.open("targets.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val version = root.optString("version", "1.98")
            val companionVersion = root.optString("companion_version", "0.1.5")
            val schemaVersion = root.optInt("schema_version", 21)

            val boardsList = mutableListOf<BoardTarget>()
            val boardsArray = root.optJSONArray("boards")
            if (boardsArray != null) {
                for (i in 0 until boardsArray.length()) {
                    val b = boardsArray.getJSONObject(i)
                    boardsList.add(
                        BoardTarget(
                            id = b.getString("id"),
                            name = b.getString("name"),
                            chip = b.optString("chip", "esp32s3"),
                            flashSize = b.optString("flash_size", "4MB"),
                            flashMode = b.optString("flash_mode", "dio"),
                            flashFreq = b.optString("flash_freq", "80m"),
                            psram = b.optString("psram", ""),
                            partitionNvs = b.optString("partition_nvs", ""),
                            hardwareId = if (b.has("hardware_id")) b.getString("hardware_id") else null,
                            factoryManifestUrl = if (b.has("factory_manifest_url")) b.getString("factory_manifest_url") else null,
                            factoryManifestSignatureUrl = if (b.has("factory_manifest_signature_url")) b.getString("factory_manifest_signature_url") else null,
                            offlineFactorySha256 = if (b.has("offline_factory_sha256")) b.getString("offline_factory_sha256") else null,
                            enabled = b.optBoolean("enabled", false)
                        )
                    )
                }
            }

            val displaysList = mutableListOf<DisplayTarget>()
            val displaysArray = root.optJSONArray("displays")
            if (displaysArray != null) {
                for (i in 0 until displaysArray.length()) {
                    val d = displaysArray.getJSONObject(i)
                    displaysList.add(
                        DisplayTarget(
                            id = d.getString("id"),
                            name = d.getString("name"),
                            resolution = d.optString("resolution", "480x320"),
                            driver = d.optString("driver", "ST7796U"),
                            interfaceType = d.optString("interface", "SPI"),
                            touch = d.optString("touch", ""),
                            pins = d.optString("pins", ""),
                            enabled = d.optBoolean("enabled", false)
                        )
                    )
                }
            }

            HardwareCatalog(version, companionVersion, schemaVersion, boardsList, displaysList)
        } catch (ex: Exception) {
            null
        }
    }
}
