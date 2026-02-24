package com.sschoi.pindog.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.sschoi.pindog.R
import com.sschoi.pindog.data.local.PinDatabase
import com.sschoi.pindog.data.local.PinEntity
import com.sschoi.pindog.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var kakaoMap: KakaoMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val db by lazy { PinDatabase.getDatabase(this) }

    // 기본 위치: 서울시청
    private val defaultLocation = LatLng.from(37.5665, 126.9780)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding.mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {
                // 지도 파괴 시 처리
            }

            override fun onMapError(error: Exception) {
                Toast.makeText(this@MainActivity, "지도 로딩 오류: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                loadSavedPins()
                
                // 지도가 준비되면 현재 위치로 이동 시도
                moveToCurrentLocation()
            }

            override fun getPosition(): LatLng {
                return defaultLocation
            }
        })

        binding.pinButton.setOnClickListener {
            addPinAtCurrentLocation()
        }
        
        binding.currentLocationFab.setOnClickListener {
            moveToCurrentLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun moveToCurrentLocation() {
        if (hasLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng.from(it.latitude, it.longitude)
                    kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(currentLatLng), CameraAnimation.from(500))
                } ?: run {
                    kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(defaultLocation))
                }
            }
        } else {
            kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(defaultLocation))
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun addPinAtCurrentLocation() {
        if (!hasLocationPermission()) {
            Toast.makeText(this, "설정에서 위치 권한을 허용해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                savePin(location.latitude, location.longitude)
            } else {
                Toast.makeText(this, "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savePin(latitude: Double, longitude: Double) {
        val latLng = LatLng.from(latitude, longitude)
        
        lifecycleScope.launch(Dispatchers.IO) {
            val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            val address = addresses?.firstOrNull()?.getAddressLine(0) ?: "주소를 찾을 수 없음"

            val newPin = PinEntity(
                latitude = latitude,
                longitude = longitude,
                address = address
            )
            db.pinDao().insertPin(newPin)
            
            withContext(Dispatchers.Main) {
                addMarker(latLng, "새로운 핀", address)
                Toast.makeText(this@MainActivity, "핀이 저장되었습니다!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addMarker(latLng: LatLng, title: String, snippet: String) {
        val layer = kakaoMap?.labelManager?.layer
        val styles = kakaoMap?.labelManager?.addLabelStyles(LabelStyles.from(LabelStyle.from(R.drawable.ic_launcher_foreground))) // 기본 아이콘 사용
        layer?.addLabel(LabelOptions.from(latLng).setStyles(styles).setTexts(title, snippet))
    }

    private fun loadSavedPins() {
        lifecycleScope.launch {
            db.pinDao().getAllPins().collect { pins ->
                kakaoMap?.labelManager?.layer?.removeAll()
                pins.forEach { pin ->
                    addMarker(LatLng.from(pin.latitude, pin.longitude), "저장된 핀", pin.address)
                }
            }
        }
    }
}
