package page.j5155.advantagescope

import android.content.Context
import android.content.res.AssetManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.internal.Streams
import com.google.gson.stream.JsonWriter
import com.qualcomm.robotcore.util.WebHandlerManager
import com.qualcomm.robotcore.util.WebServer
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import org.firstinspires.ftc.ftccommon.external.WebHandlerRegistrar
import org.firstinspires.ftc.robotcore.internal.collections.SimpleGson
import org.firstinspires.ftc.robotcore.internal.system.AppUtil
import org.firstinspires.ftc.robotcore.internal.webserver.WebHandler
import org.firstinspires.ftc.robotserver.internal.webserver.MimeTypesUtil
import org.firstinspires.ftc.robotserver.internal.webserver.RobotControllerWebHandlers
import java.io.File
import java.io.StringWriter


object AdvantageScopeLite {
    const val WEB_ROOT = "/as"
    const val EXTRA_ASSETS_PATH = "ascope_assets/"
    val EXTRA_ASSETS = File(AppUtil.ROOT_FOLDER, EXTRA_ASSETS_PATH)

    init {
        if (!EXTRA_ASSETS.exists()) EXTRA_ASSETS.mkdir()
    }

    const val BUNDLED_ASSETS_PATH = "as/bundledAssets/"
    val ALLOWED_LOG_SUFFIXES = arrayOf(".wpilog", ".rlog", ".log")

    val jsonParser = JsonParser()

    val bundledAssets = JsonObject()

    lateinit var webHandlerManager: WebHandlerManager
    val assetManager: AssetManager = AppUtil.getInstance().modalContext.assets

    init {
        // read and cache config.json for all bundled assets
        // bundled assets cannot change without an app restart, so caching is completely safe
        val bundledAssetFiles = assetManager.list(BUNDLED_ASSETS_PATH)
        // don't use .walk() here for searching recursively; no assetManager function for it
        // we control bundledAssets so it won't ever be an issue (TM)
        bundledAssetFiles?.forEach { assetPath ->
            val containedFiles = assetManager.list(BUNDLED_ASSETS_PATH + assetPath)
            containedFiles?.forEach { filename ->
                val path = "$assetPath/$filename"
                if (filename == "config.json") {
                    val file = assetManager.open(BUNDLED_ASSETS_PATH + path).bufferedReader()
                    bundledAssets.add(path, jsonParser.parse(file))
                    file.close()
                } else {
                    bundledAssets.add(path, null)
                }
            }
        }
    }

    var extraAssets = JsonObject()

    init {
        readExtraAssets()
    }

    private fun readExtraAssets() {
        // read and cache config.json for all extra assets
        val newExtraAssets = JsonObject()
        // use .walk() here to search recursively in subfolders
        EXTRA_ASSETS.walk().forEach { file ->
            val path = file.toRelativeString(EXTRA_ASSETS)
            if (file.name == "config.json") {
                val fileReader = file.reader()
                newExtraAssets.add(path, jsonParser.parse(fileReader))
                fileReader.close()
            } else {
                newExtraAssets.add(path, null)
            }
        }
    }

    @Suppress("unused")
    @WebHandlerRegistrar
    @JvmStatic
    fun attachWebServer(context: Context?, manager: WebHandlerManager) {
        internalAttachWebServer(manager.webServer)
    }

    private fun internalAttachWebServer(webServer: WebServer?) {
        if (webServer == null) {
            return
        }



        webHandlerManager = webServer.webHandlerManager

        // register web static assets
        registerAssetsUnderPath(webHandlerManager, assetManager, "as", "bundledAssets")
        // register AS bundled and extra assets
        registerASAssets()
        // Redirect for trailing slash (or frontend doesn't work)
        webHandlerManager.register(
            WEB_ROOT,
            RobotControllerWebHandlers.Redirection("$WEB_ROOT/")
        )
        webHandlerManager.register(
            "$WEB_ROOT/",
            newStaticAssetHandler(assetManager, "as/index.html")
        )

        webHandlerManager.register(
            "$WEB_ROOT/assets",
            assetListHandler()
        )
        webHandlerManager.register(
            "$WEB_ROOT/assets/",
            assetListHandler()
        )

        webHandlerManager.register(
            "$WEB_ROOT/logs",
            logListHandler()
        )
        webHandlerManager.register(
            "$WEB_ROOT/logs/",
            logListHandler()
        )

        webHandlerManager.register(
            "$WEB_ROOT/uploadAsset",
            AssetUpload(EXTRA_ASSETS)
        )
        webHandlerManager.register(
            "$WEB_ROOT/uploadAsset/",
            AssetUpload(EXTRA_ASSETS)
        )
    }

    private fun registerASAssets() {
        bundledAssets.entrySet().forEach { (path, _) ->
            webHandlerManager.register(
                "$WEB_ROOT/assets/$path",
                newStaticAssetHandler(assetManager, BUNDLED_ASSETS_PATH + path)
            )
        }
        extraAssets.entrySet().forEach { (path, _) ->
            webHandlerManager.register("$WEB_ROOT/assets/$path", newStaticFileHandler(EXTRA_ASSETS_PATH + path))
        }
    }


    private fun assetListHandler(): WebHandler {
        return object : WebHandler {
            override fun getResponse(session: IHTTPSession): NanoHTTPD.Response {
                if (session.method == NanoHTTPD.Method.GET) {
                    readExtraAssets()
                    extraAssets.entrySet().forEach { (path, _) ->
                        webHandlerManager.register(
                            "$WEB_ROOT/assets/$path",
                            newStaticFileHandler(EXTRA_ASSETS_PATH + path)
                        )
                    }

                    // order matters;
                    // if an extra asset is added with the same name as a bundled asset,
                    // it will override the bundled one
                    val assetFileList = bundledAssets + extraAssets


                    val jsonStringWriter = StringWriter()
                    val jsonWriter = JsonWriter(jsonStringWriter)
                    jsonWriter.serializeNulls = true
                    Streams.write(assetFileList, jsonWriter)

                    val jsonString = jsonStringWriter.buffer.toString()

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

    data class LogFile(@JvmField val name: String, @JvmField val size: Int)

    // Direct, 1:1 port of the log list handling in AdvantageScope lite_server.py
    // Originally copyright Littleton Robotics. Used under the BSD-3-Clause license.
    private fun logListHandler(): WebHandler {
        return WebHandler {
            val files = ArrayList<LogFile>()
            if (it.parameters.keys.contains("folder") && it.parameters["folder"]!!.isNotEmpty()) {
                val folderPath = it.parameters["folder"]!![0]
                val folder = File(AppUtil.ROOT_FOLDER, folderPath)
                if (!folder.exists() || !folder.isDirectory) return@WebHandler NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND,
                    NanoHTTPD.MIME_PLAINTEXT, "Requested folder does not exist"
                )

                for (file in folder.listFiles()!!) {
                    for (suffix in ALLOWED_LOG_SUFFIXES) {
                        if (file.name.endsWith(suffix)) {
                            // technically toInt fails if the log is larger than 4 GB
                            files.add(LogFile(file.name, file.length().toInt()))
                            webHandlerManager.register("$WEB_ROOT/logs/${file.name}", newStaticFileHandler(file))
                            break
                        }
                    }
                }

                val jsonString = SimpleGson.getInstance().toJson(files)
                return@WebHandler NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    MimeTypesUtil.MIME_JSON,
                    jsonString
                )

            } else {
                return@WebHandler NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND,
                    NanoHTTPD.MIME_PLAINTEXT, "No folder specified!"
                )
            }

        }
    }
}

