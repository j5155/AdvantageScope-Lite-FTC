package page.j5155.rrscopelite

import android.content.Context
import android.content.res.AssetManager
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


object RRScopeLite {
    init {
        RobotLog.i("RRScopeLite START")
    }
    const val WEB_ROOT = "as"
    const val EXTRA_ASSETS_PATH = "ascope_assets/"
    val EXTRA_ASSETS = File(AppUtil.ROOT_FOLDER, EXTRA_ASSETS_PATH)
    const val BUNDLED_ASSETS_PATH = "as/bundledAssets/"
    val ALLOWED_LOG_SUFFIXES = arrayOf(".wpilog", ".rlog", ".log")

    val jsonParser = JsonParser()

    val bundledAssets = JsonObject()

    lateinit var webHandlerManager: WebHandlerManager
    val assetManager: AssetManager = AppUtil.getInstance().modalContext.assets

    init {
        // read and cache config.json for all bundled assets
        // bundled assets cannot change without an app restart, so caching is completely safe
        val assetManager = AppUtil.getInstance().modalContext.assets
        val bundledAssetFiles = assetManager.list(BUNDLED_ASSETS_PATH)
        bundledAssetFiles?.forEach { assetPath ->
            val containedFiles = assetManager.list(BUNDLED_ASSETS_PATH + assetPath)
            containedFiles?.forEach { filename ->
                val path = "$assetPath/$filename"
                if (filename == "config.json") {
                    val file = assetManager.open(path).bufferedReader()
                    bundledAssets.add(path, jsonParser.parse(file))
                    file.close()
                } else {
                    bundledAssets.add(path,null)
                }
            }
        }
    }

    val extraAssets = JsonObject()

    init {
        // read and cache config.json for all extra assets
        // ideally we would handle adding extra assets without an app restart, but this will work for now
        val extraAssetFiles = EXTRA_ASSETS.listFiles()
        extraAssetFiles?.forEach { assetFile ->
            val containedFiles = assetFile.listFiles()
            containedFiles?.forEach { file ->
                val path = assetFile.name + "/" +file.name
                if (file.name == "config.json") {
                    val fileReader = file.reader()

                    extraAssets.add(path, jsonParser.parse(fileReader))
                    fileReader.close()
                } else {
                    extraAssets.add(path,null)
                }
            }
        }
    }

    @Suppress("unused")
    @WebHandlerRegistrar
    @JvmStatic
    fun attachWebServer(context: Context?, manager: WebHandlerManager) {
        RobotLog.i("RRScopeLite attachWebServer")
        internalAttachWebServer(manager.webServer)
    }

    private fun internalAttachWebServer(webServer: WebServer?) {
        if (webServer == null) {
            return
        }

        webHandlerManager = webServer.webHandlerManager
        webHandlerManager.register(
            "/$WEB_ROOT",
            newStaticAssetHandler(assetManager, "as/index.html")
        )
        webHandlerManager.register(
            "/$WEB_ROOT/",
            newStaticAssetHandler(assetManager, "as/index.html")
        )
        webHandlerManager.register(
            "/$WEB_ROOT/assets",
            assetListHandler()
        )
        webHandlerManager.register(
            "/$WEB_ROOT/assets/",
            assetListHandler()
        )
        // register web static assets
        registerAssetsUnderPath(webHandlerManager, assetManager, "as", "bundledAssets")
        // register AS bundled and extra assets
        registerASAssets(webHandlerManager)
    }

    private fun registerASAssets(webHandlerManager: WebHandlerManager) {
        bundledAssets.entrySet().forEach { (path, _) ->
            webHandlerManager.register(path,newStaticAssetHandler(assetManager,BUNDLED_ASSETS_PATH + path))
        }
        extraAssets.entrySet().forEach{ (path, _) ->
            webHandlerManager.register(path, newStaticFileHandler(EXTRA_ASSETS_PATH + path))
        }
    }


    private fun assetListHandler(): WebHandler {
        return object : WebHandler {
            override fun getResponse(session: IHTTPSession): NanoHTTPD.Response {
                if (session.method == NanoHTTPD.Method.GET) {
                    // order matters;
                    // if an extra asset is added with the same name as a bundled asset,
                    // it will override the bundled one
                    val assetFileList = bundledAssets + extraAssets

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

