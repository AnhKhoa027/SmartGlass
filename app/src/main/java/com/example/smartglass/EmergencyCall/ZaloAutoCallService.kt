//package com.example.smartglass.EmergencyCall
//
//import android.accessibilityservice.AccessibilityService
//import android.util.Log
//import android.view.accessibility.AccessibilityEvent
//import android.view.accessibility.AccessibilityNodeInfo
//
//class ZaloAutoCallService : AccessibilityService() {
//
//    private var tryCount = 0
//
//    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
//        if (event?.packageName != "com.zing.zalo") return
//
//        val root = rootInActiveWindow ?: return
//        autoClickCallButton(root)
//    }
//
//    private fun autoClickCallButton(root: AccessibilityNodeInfo) {
//
//        // === Cách 1: tìm nút "Gọi nhóm" dạng Text ===
//        val groupCall = root.findAccessibilityNodeInfosByText("Gọi nhóm")
//        if (!groupCall.isNullOrEmpty()) {
//            groupCall[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
//            Log.d("ZALO_CALL", "CLICKED: Text 'Gọi nhóm'")
//            return
//        }
//
//        // === Cách 2: tìm icon CALL bằng id mới (Zalo bản 2024–2025) ===
//        val callIcon = root.findAccessibilityNodeInfosByViewId("com.zing.zalo:id/ic_call_voice")
//        if (!callIcon.isNullOrEmpty()) {
//            callIcon[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
//            Log.d("ZALO_CALL", "CLICKED: ic_call_voice")
//            return
//        }
//
//        // === Cách 3: tìm video call và suy ra voice call (Video nằm cạnh Voice) ===
//        val videoIcon = root.findAccessibilityNodeInfosByViewId("com.zing.zalo:id/ic_call_video")
//        if (!videoIcon.isNullOrEmpty()) {
//            val parent = videoIcon[0].parent
//            if (parent != null && parent.childCount > 1) {
//                val voiceButton = parent.getChild(0)
//                voiceButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
//                Log.d("ZALO_CALL", "CLICKED: Sibling voice button")
//                return
//            }
//        }
//
//        // === Cách 4: tìm ImageButton top-right ===
//        val allImages = ArrayList<AccessibilityNodeInfo>()
//        collectNodesByClass(root, "android.widget.ImageButton", allImages)
//
//        for (node in allImages) {
//            val rect = android.graphics.Rect()
//            node.getBoundsInScreen(rect)
//
//            if (node.isClickable && rect.right > 900 && rect.top < 300) {
//                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
//                Log.d("ZALO_CALL", "CLICKED: ImageButton top-right")
//                return
//            }
//        }
//
//
//        // === Cách 5: retry 5 lần để chờ UI Zalo load ===
//        if (tryCount < 5) {
//            tryCount++
//            root.refresh()
//            Log.d("ZALO_CALL", "Retry find call button ($tryCount)")
//        }
//    }
//
//    private fun collectNodesByClass(node: AccessibilityNodeInfo, className: String, out: MutableList<AccessibilityNodeInfo>) {
//        if (node.className == className) {
//            out.add(node)
//        }
//        for (i in 0 until node.childCount) {
//            val child = node.getChild(i) ?: continue
//            collectNodesByClass(child, className, out)
//        }
//    }
//
//    override fun onInterrupt() {}
//}
