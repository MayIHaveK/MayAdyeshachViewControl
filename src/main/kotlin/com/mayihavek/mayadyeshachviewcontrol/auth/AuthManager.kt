package com.mayihavek.mayadyeshachviewcontrol.auth

import taboolib.common.platform.function.info
import taboolib.common.platform.function.severe
import taboolib.common.platform.function.warning
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object AuthManager {

    private const val API_KEY = "MC_E6A58195A5BA49E8AFFDF177F667A569"
    private val TOKEN_SEED = byteArrayOf(
        0x4D, 0x41, 0x56, 0x43, 0x41, 0x75, 0x74, 0x68,
        0x56, 0x65, 0x72, 0x69, 0x66, 0x79, 0x56, 0x31
    )

    @Volatile
    private var sessionSig: Long = 0L

    @Volatile
    private var sessionEpoch: Int = 0

    fun performValidation(): Boolean {
        return try {
            val hwid = generateHWID()
            val bindQQ = loadBindQQ()

            if (bindQQ.isNullOrEmpty()) {
                info("HWID: $hwid")
                severe("请在 plugins/lantong/MayAdyeshachViewControl.yml 中填写 bind-qq 后重启服务器")
                return false
            }

            val validated = cloudValidate(bindQQ, hwid)
            if (!validated) {
                severe("验证失败！HWID: $hwid")
                severe("请联系管理员 2534226689 购买授权")
                return false
            }

            sessionEpoch = computeEpoch(hwid)
            sessionSig = computeSig(hwid, sessionEpoch)
            info("授权验证成功")
            true
        } catch (e: Exception) {
            severe("验证过程异常: ${e.message}")
            false
        }
    }

    fun checkIntegrity(): Boolean {
        if (sessionEpoch == 0 && sessionSig == 0L) return false
        return try {
            val hwid = generateHWID()
            val expected = computeSig(hwid, sessionEpoch)
            expected == sessionSig
        } catch (_: Exception) {
            false
        }
    }

    fun clearSession() {
        sessionSig = 0L
        sessionEpoch = 0
    }

    private fun loadBindQQ(): String? {
        val pluginsDir = File("plugins")
        val lantongDir = File(pluginsDir, "lantong")
        if (!lantongDir.exists()) lantongDir.mkdirs()
        val authFile = File(lantongDir, "MayAdyeshachViewControl.yml")
        if (!authFile.exists()) {
            authFile.writeText("bind-qq: ''\n")
            return null
        }
        val lines = authFile.readLines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("bind-qq:")) {
                return trimmed.substringAfter("bind-qq:")
                    .trim().removeSurrounding("'").removeSurrounding("\"").trim()
            }
        }
        return null
    }

    private fun computeEpoch(hwid: String): Int {
        var h = 0x811c9dc5.toInt()
        for (c in hwid) {
            h = h xor c.code
            h = (h.toLong() * 0x01000193L).toInt()
        }
        h = h xor (System.currentTimeMillis() ushr 16).toInt()
        return h
    }

    private fun computeSig(hwid: String, epoch: Int): Long {
        var sig = -0x340d631b7bdddcdbL
        for (c in hwid) {
            sig = sig xor c.code.toLong()
            sig *= 0x100000001b3L
        }
        sig = sig xor ((epoch.toLong() shl 17) or (epoch.toLong() ushr 15))
        for (b in TOKEN_SEED) {
            sig = sig xor b.toLong()
            sig *= 0x100000001b3L
        }
        return sig
    }

    private fun generateHWID(): String {
        val computerName = System.getenv("COMPUTERNAME")
        val localHost = InetAddress.getLocalHost()
        val ipAddress = localHost.hostAddress
        val input = "${computerName ?: "unknown"}|$ipAddress"
        val md5 = MessageDigest.getInstance("MD5").digest(input.toByteArray(StandardCharsets.UTF_8))
        val sb = StringBuilder()
        for (b in md5) {
            sb.append(b)
        }
        return sb.toString()
    }

    private fun cloudValidate(bindQQ: String, hwid: String): Boolean {
        return try {
            val url = URL("http://43.139.222.77:5173/api/auth/validate")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true

            val body = """{"qq":"$bindQQ","apiKey":"$API_KEY","hardwareId":"$hwid"}"""
            conn.outputStream.use { os: OutputStream ->
                os.write(body.toByteArray(StandardCharsets.UTF_8))
            }

            val stream = if (conn.responseCode < 300) conn.inputStream else conn.errorStream
            val response = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }

            parseJsonSuccess(response)
        } catch (e: Exception) {
            warning("云端验证请求失败: ${e.message}")
            false
        }
    }

    private fun parseJsonSuccess(jsonStr: String): Boolean {
        return try {
            val gsonClass = try {
                Class.forName("com.google.gson.Gson")
            } catch (_: ClassNotFoundException) {
                Class.forName("org.bukkit.craftbukkit.libs.com.google.gson.Gson")
            }

            val jsonObjectCls = try {
                Class.forName("com.google.gson.JsonObject")
            } catch (_: ClassNotFoundException) {
                Class.forName("org.bukkit.craftbukkit.libs.com.google.gson.JsonObject")
            }

            val gson = gsonClass.getDeclaredConstructor().newInstance()
            val fromJson = gsonClass.getMethod("fromJson", String::class.java, Class::class.java)
            val jsonObj = fromJson.invoke(gson, jsonStr, jsonObjectCls)

            val hasMethod = jsonObjectCls.getMethod("has", String::class.java)
            if (hasMethod.invoke(jsonObj, "success") as Boolean) {
                val getMethod = jsonObjectCls.getMethod("get", String::class.java)
                val successElement = getMethod.invoke(jsonObj, "success")
                val getAsBoolean = successElement.javaClass.getMethod("getAsBoolean")
                getAsBoolean.invoke(successElement) as Boolean
            } else {
                false
            }
        } catch (e: Exception) {
            severe("JSON 解析错误: ${e.message}")
            false
        }
    }
}
