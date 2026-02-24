package com.sschoi.pindog

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.sschoi.pindog.data.local.PinDatabase
import com.sschoi.pindog.data.local.PinEntity
import com.sschoi.pindog.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val db by lazy { PinDatabase.getDatabase(this) }
    
    // 현재 임시로 찍혀있는 핀을 추적하기 위한 변수
    private var tempMarker: Marker? = null
    private var lastSelectedLocation: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 1. 현재 위치 아이콘 클릭 시
        binding.currentLocationFab.setOnClickListener {
            moveToCurrentLocation()
        }

        // 2. 핀 고정 버튼 클릭 시 (DB 저장)
        binding.pinButton.setOnClickListener {
            saveCurrentPin()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        checkLocationPermission()
        loadSavedPins() // 기존에 저장된 핀들 불러오기
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }
        mMap.isMyLocationEnabled = true
        moveToCurrentLocation() // 시작 시 현재 위치로 이동
    }

    private fun moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val currentLatLng = LatLng(it.latitude, it.longitude)
                lastSelectedLocation = currentLatLng
                
                // 지도 카메라 이동
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f))

                // 기존 임시 핀 제거 후 새로 생성
                tempMarker?.remove()
                tempMarker = mMap.addMarker(
                    MarkerOptions()
                        .position(currentLatLng)
                        .title("여기를 북마크할까요?")
                        .alpha(0.7f) // 임시 핀은 약간 투명하게
                )
                tempMarker?.showInfoWindow()
            }
        }
    }

    private fun saveCurrentPin() {
        val location = lastSelectedLocation
        if (location == null) {
            Toast.makeText(this, "먼저 현재 위치를 잡아주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            // 주소 획득
            val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            val address = addresses?.firstOrNull()?.getAddressLine(0) ?: "주소 정보 없음"

            // DB 저장
            val newPin = PinEntity(
                latitude = location.latitude,
                longitude = location.longitude,
                address = address
            )
            db.pinDao().insertPin(newPin)

            withContext(Dispatchers.Main) {
                // 임시 핀을 실제 저장된 핀 스타일로 변경
                tempMarker?.remove()
                mMap.addMarker(
                    MarkerOptions()
                        .position(location)
                        .title("저장 완료!")
                        .snippet(address)
                )?.showInfoWindow()
                
                tempMarker = null // 임시 핀 상태 초기화
                Toast.makeText(this@MainActivity, "핀이 저장되었습니다!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSavedPins() {
        lifecycleScope.launch {
            db.pinDao().getAllPins().collect { pins ->
                // 저장된 핀들을 지도에 표시 (이미 있는 마커와 겹치지 않게 필요시 clear 호출)
                pins.forEach { pin ->
                    mMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(pin.latitude, pin.longitude))
                            .title("내 핀")
                            .snippet(pin.address)
                    )
                }
            }
        }
    }
}
