package com.example.smartglass.Navigation

interface NavigationCallback {
    // Hàm này sẽ được VoiceCommandProcessor gọi khi có lệnh điều hướng
    fun startNavigationTo(destination: String)

    // Hàm để dừng điều hướng
    fun stopNavigation()
}