package com.example.smartglass.gps

object GoongStepsParser {
    fun parseSteps(response: GoongDirectionResponse?): List<GoongStep> {

        // 1. Lấy route đầu tiên
        val route = response?.routes?.firstOrNull() // -> Lấy GoongRoute (distance=5000.0)

        // 2. Lấy leg đầu tiên
        val leg = route?.legs?.firstOrNull() // -> Lấy GoongLeg (chứa 2 steps)

        // 3. Lấy danh sách steps
        val stepsList = leg?.steps // -> Lấy List<GoongStep> có 2 phần tử (BƯỚC 1 và BƯỚC 2)

        // 4. Trả về danh sách đó
        return stepsList ?: emptyList()
    }
}