package com.example.homelab2.model
import com.google.gson.annotations.SerializedName

data class HouseConfig(
    @SerializedName("target_temperature")
    val targetTemperature: Double,

    @SerializedName("living_room_lights")
    val livingRoomLights: String,

    @SerializedName("hvac_mode")
    val hvacMode: String,

    @SerializedName("security_system")
    val securitySystem: String,

    @SerializedName("last_updated_by")
    val lastUpdatedBy: String
)
