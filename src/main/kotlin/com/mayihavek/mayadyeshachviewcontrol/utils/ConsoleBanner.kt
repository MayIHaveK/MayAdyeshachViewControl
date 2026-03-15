package com.mayihavek.mayadyeshachviewcontrol.utils

import com.github.lalyos.jfiglet.FigletFont
import taboolib.common.platform.function.console

/**
 * 现代化控制台横幅打印工具类
 * 使用纯 ANSI RGB 颜色，支持渐变效果
 * 
 * @author MayIHaveK
 * @since 2026/3/15
 */
class ConsoleBanner private constructor() {

    // --- 基础配置 ---
    var asciiText: String = "PLUGIN"
    var pluginName: String = "Unknown Plugin"
    var version: String = "1.0.0"
    var author: String = "MayIHaveK"
    var contact: String = ""

    // --- 主题配置 (Hex) ---
    var accentStart: String = "00D4FF"   // 霓虹青
    var accentEnd: String = "9D4EDD"     // 电紫
    var successColor: String = "00FF88"  // 薄荷绿
    var textColor: String = "FFFFFF"     // 纯白
    var dimColor: String = "A0A0A0"      // 浅灰（更亮）

    // --- 动态内容区 ---
    private val infoItems = mutableListOf<Pair<String, String>>()

    fun info(label: String, value: String) {
        infoItems.add(label to value)
    }

    companion object {
        private const val BOLD = "\u001B[1m"
        private const val RESET = "\u001B[0m"

        fun print(block: ConsoleBanner.() -> Unit) {
            val banner = ConsoleBanner().apply(block)
            banner.render()
        }

        private fun rgb(hex: String): String {
            val h = hex.removePrefix("#")
            val r = h.substring(0, 2).toInt(16)
            val g = h.substring(2, 4).toInt(16)
            val b = h.substring(4, 6).toInt(16)
            return "\u001B[38;2;${r};${g};${b}m"
        }
    }

    private fun out(text: String) {
        console().sendMessage(text)
    }

    private fun render() {
        out("")
        printTopBorder()
        printLogo()
        printDivider()
        printPluginInfo()
        printInfoSection()
        printBottomBorder()
        out("")
    }

    private fun printTopBorder() {
        out("  ${GradientText.generate("╭──────────────────────────────────────────────────────", accentStart, accentEnd)}")
    }

    private fun printDivider() {
        out("  ${GradientText.generate("├──────────────────────────────────────────────────────", accentEnd, accentStart)}")
    }

    private fun printBottomBorder() {
        out("  ${GradientText.generate("╰──────────────────────────────────────────────────────", accentStart, accentEnd)}")
    }

    private fun printLogo() {
        try {
            val rawAscii = FigletFont.convertOneLine(asciiText)
            val lines = rawAscii.split("\n").filter { it.isNotBlank() }
            
            lines.forEach { line ->
                out("  ${rgb(accentStart)}│$RESET  $BOLD${GradientText.generate(line, accentStart, accentEnd)}$RESET")
            }
        } catch (e: Exception) {
            out("  ${rgb(accentStart)}│$RESET  $BOLD${GradientText.generate("◆ $asciiText ◆", accentStart, accentEnd)}$RESET")
        }
    }

    private fun printPluginInfo() {
        // 第一行：插件名 + 版本 + 状态
        val statusIcon = "●"
        out("  ${rgb(accentStart)}│$RESET  $BOLD${rgb(textColor)}$pluginName$RESET  ${rgb(dimColor)}v$version$RESET    ${rgb(successColor)}$statusIcon RUNNING$RESET")
        
        // 第二行：作者信息
        val authorInfo = if (contact.isNotEmpty()) "by $author  •  $contact" else "by $author"
        out("  ${rgb(accentStart)}│$RESET  ${rgb(dimColor)}$authorInfo$RESET")
    }

    private fun printInfoSection() {
        if (infoItems.isEmpty()) return
        
        // 空行分隔
        out("  ${rgb(accentStart)}│$RESET")
        
        infoItems.forEach { (label, value) ->
            out("  ${rgb(accentStart)}│$RESET  ${rgb(accentEnd)}▸$RESET ${rgb(textColor)}$label:$RESET ${rgb(successColor)}$value$RESET")
        }
    }
}