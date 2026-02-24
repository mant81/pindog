package com.sschoi.pindog.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pins")
data class PinEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val latitude: Double,      // 위도
    val longitude: Double,     // 경도
    val address: String,       // 변환된 주소 (Geocoder 활용 예정)
    val timestamp: Long = System.currentTimeMillis() // 저장된 시간
)
