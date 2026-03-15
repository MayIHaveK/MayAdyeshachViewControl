package com.mayihavek.mayadyeshachviewcontrol.utils

/**
 * 工业级 ANSI RGB 渐变文字生成工具
 * @author MayIHaveK
 * @Date 2026/3/15
 */
object GradientText {

    private const val ANSI_START = "\u001B[38;2;"
    private const val ANSI_END = "m"
    private const val ANSI_RESET = "\u001B[0m"

    /**
     * 生成带 ANSI 转义序列的 RGB 渐变字符串
     * * @param text 要渐变的文本
     * @param startHex 起始颜色 (例如 "00FFFF" 或 "#00FFFF")
     * @param endHex 结束颜色 (例如 "B266FF" 或 "#B266FF")
     * @return 带有完整 ANSI 颜色代码的字符串
     */
    fun generate(text: String, startHex: String, endHex: String): String {
        if (text.isEmpty()) return ""

        val startRGB = hexToRGB(startHex)
        val endRGB = hexToRGB(endHex)

        // 单字符直接返回纯色，避免除以 0 的异常
        if (text.length == 1) {
            return wrapAnsi(text, startRGB)
        }

        val builder = StringBuilder()
        val length = text.length

        for (i in 0 until length) {
            // 计算当前字符的渐变比例 (0.0 到 1.0)
            val ratio = i.toDouble() / (length - 1)

            // 线性插值 (Lerp) 计算当前字符的 RGB
            val r = (startRGB[0] + (endRGB[0] - startRGB[0]) * ratio).toInt()
            val g = (startRGB[1] + (endRGB[1] - startRGB[1]) * ratio).toInt()
            val b = (startRGB[2] + (endRGB[2] - startRGB[2]) * ratio).toInt()

            builder.append(ANSI_START)
                .append(r).append(";")
                .append(g).append(";")
                .append(b).append(ANSI_END)
                .append(text[i])
        }

        builder.append(ANSI_RESET)
        return builder.toString()
    }

    /**
     * 辅助方法：将单个字符或文本包裹为单色 ANSI
     */
    private fun wrapAnsi(text: String, rgb: IntArray): String {
        return "$ANSI_START${rgb[0]};${rgb[1]};${rgb[2]}$ANSI_END$text$ANSI_RESET"
    }

    /**
     * 辅助方法：Hex 字符串转 RGB 数组，自带防崩溃保护
     */
    private fun hexToRGB(hex: String): IntArray {
        // 自动兼容带 "#" 和不带 "#" 的输入
        val cleanHex = hex.removePrefix("#")

        // toIntOrNull 防止输入非法的颜色代码导致插件无法启动
        // 如果输入错误，默认回退到纯白色 (0xFFFFFF)
        val color = cleanHex.toIntOrNull(16) ?: 0xFFFFFF

        return intArrayOf(
            (color shr 16) and 0xFF, // R (红)
            (color shr 8) and 0xFF,  // G (绿)
            color and 0xFF           // B (蓝)
        )
    }
}