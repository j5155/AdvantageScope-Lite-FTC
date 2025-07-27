package page.j5155.rrscopelite

import android.R.attr.mimeType
import android.R.attr.path
import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.qualcomm.robotcore.util.RobotLog
import com.qualcomm.robotcore.util.WebHandlerManager
import com.qualcomm.robotcore.util.WebServer
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import org.firstinspires.ftc.ftccommon.external.WebHandlerRegistrar
import org.firstinspires.ftc.robotcore.internal.collections.SimpleGson
import org.firstinspires.ftc.robotcore.internal.system.AppUtil
import org.firstinspires.ftc.robotcore.internal.webserver.WebHandler
import org.firstinspires.ftc.robotserver.internal.webserver.MimeTypesUtil
import java.io.File
import java.io.IOException

/* Directly copied from Road Runner FTC: full credit to acmerobotics
     * Used under the following license:
     *
     * MIT License
     *
     * Copyright (c) 2018-2022 ACME Robotics
     *
     * Permission is hereby granted, free of charge, to any person obtaining a copy
     * of this software and associated documentation files (the "Software"), to deal
     * in the Software without restriction, including without limitation the rights
     * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
     * copies of the Software, and to permit persons to whom the Software is
     * furnished to do so, subject to the following conditions:
     *
     * The above copyright notice and this permission notice shall be included in all
     * copies or substantial portions of the Software.
     *
     * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
     * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
     * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
     * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
     * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
     * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
     * SOFTWARE.
     */

private fun newStaticAssetHandler(assetManager: AssetManager, file: String): WebHandler {
    return WebHandler { session: IHTTPSession ->
        if (session.method == NanoHTTPD.Method.GET) {
            val mimeType = MimeTypesUtil.determineMimeType(file)
            NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK,
                mimeType, assetManager.open(file))
        } else {
            NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                NanoHTTPD.MIME_PLAINTEXT, "")
        }
    }
}

private fun registerAssetsUnderPath(webHandlerManager: WebHandlerManager, assetManager: AssetManager, path: String) {
    try {
        val list = assetManager.list("web/$path") ?: return
        if (list.isNotEmpty()) {
            for (file in list) {
                registerAssetsUnderPath(webHandlerManager, assetManager, "$path/$file")
            }
        } else {
            webHandlerManager.register("/$path", newStaticAssetHandler(assetManager, "web/$path"))
        }
    } catch (e: IOException) {
        RobotLog.setGlobalErrorMsg(RuntimeException(e),
            "unable to register tuning web routes")
    }
}

object RRScopeLite {
    const val TAG = "RRScopeLite"

    @WebHandlerRegistrar
    fun attachWebServer(context: Context?, manager: WebHandlerManager) {
        internalAttachWebServer(manager.webServer)
    }

    private fun internalAttachWebServer(webServer: WebServer?) {
        if (webServer == null) {
            return
        }

        val activity = AppUtil.getInstance().activity

        if (activity == null) {
            return
        }

        val webHandlerManager = webServer.webHandlerManager
        val assetManager = activity.assets
        webHandlerManager.register(
            "/as",
            newStaticAssetHandler(assetManager, "as/index.html")
        )
        webHandlerManager.register(
            "/as/",
            newStaticAssetHandler(assetManager, "as/index.html")
        )
        webHandlerManager.register(
            "/as/assets",
            assetListHandler(assetManager)
        )
        webHandlerManager.register(
            "/as/assets/",
            assetListHandler(assetManager)
        )
        registerAssetsUnderPath(webHandlerManager, assetManager, "as")
    }




    // Ported from AdvantageScope lite_server.py

    val EXTRA_ASSETS_PATH  = "ascope_assets"
    val EXTRA_ASSETS = File(AppUtil.ROOT_FOLDER, EXTRA_ASSETS_PATH)
    val BUNDLED_ASSETS_PATH = "as/bundledAssets"
    val ALLOWED_LOG_SUFFIXES = arrayOf(".wpilog", ".rlog", ".log")

    val jsonParser = JsonParser()


    private fun assetListHandler(assetManager: AssetManager): WebHandler {
        return object : WebHandler {
            override fun getResponse(session: IHTTPSession): NanoHTTPD.Response {
                if (session.method == NanoHTTPD.Method.GET) {
                    val assetFileList = JsonObject()
                    val bundledAssets = assetManager.list(BUNDLED_ASSETS_PATH)
                    bundledAssets?.forEach { assetPath ->
                        val containedFiles = assetManager.list(BUNDLED_ASSETS_PATH + assetPath)
                        containedFiles?.forEach { filename ->
                            if (filename == "config.json") {
                                val path = BUNDLED_ASSETS_PATH + assetPath + filename
                                val file = assetManager.open(path).bufferedReader()
                                assetFileList.add(path, jsonParser.parse(file))
                                file.close()
                            }
                        }
                    }

                    val extraAssets = EXTRA_ASSETS.listFiles()
                    extraAssets?.forEach { assetFile ->
                        val containedFiles = assetFile.listFiles()
                        containedFiles?.forEach { file ->
                            if (file.name == "config.json") {
                                val path = file.absolutePath
                                val fileReader = file.reader()
                                assetFileList.add(path, jsonParser.parse(fileReader))
                                fileReader.close()
                            }
                        }
                    }

                    val jsonString = SimpleGson.getInstance().toJson(assetFileList)

                    return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK,
                        MimeTypesUtil.MIME_JSON, jsonString
                    )
                } else {
                    return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.NOT_FOUND,
                        NanoHTTPD.MIME_PLAINTEXT, ""
                    )
                }
            }
        }
    }
}

