package com.duckboyau.pokitbridge

import java.util.UUID

object PokitUuids {
    val MM_SERVICE: UUID = UUID.fromString("e7481d2f-5781-442e-bb9a-fd4e3441dadc")
    val MM_SETTINGS: UUID = UUID.fromString("53dc9a7a-bc19-4280-b76b-002d0e23b078")
    val MM_READING: UUID = UUID.fromString("047d3559-8bee-423a-b229-4417fa603b90")

    val STATUS_SERVICE: UUID = UUID.fromString("57d3a771-267c-4394-8872-78223e92aec5")
    val STATUS_CHAR: UUID = UUID.fromString("3dba36e1-6120-4706-8dfd-ed9c16e569b6")

    val DIS_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val DIS_MANUFACTURER: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
    val DIS_MODEL: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    val DIS_FIRMWARE: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val ADVERT_NAME = "PokitPro"
    const val WATCH_URL = ""
}
