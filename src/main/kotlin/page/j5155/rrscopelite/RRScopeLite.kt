package page.j5155.rrscopelite

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.qualcomm.robotcore.util.WebHandlerManager
import com.qualcomm.robotcore.util.WebServer
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import org.firstinspires.ftc.ftccommon.external.WebHandlerRegistrar
import org.firstinspires.ftc.robotcore.internal.system.AppUtil
import org.firstinspires.ftc.robotcore.internal.webserver.WebHandler
import org.firstinspires.ftc.robotserver.internal.webserver.MimeTypesUtil
import java.io.IOException


object RRScopeLite {
    const val TAG = "RRScopeLite"

    /* Directly copied from FTC Dashboard: full credit to acmerobotics
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
    @WebHandlerRegistrar
    fun attachWebServer(context: Context, manager: WebHandlerManager) {
        internalAttachWebServer(manager.webServer)
    }

    private fun newStaticAssetHandler(assetManager: AssetManager, file: String): WebHandler {
        return object : WebHandler {
            override fun getResponse(session: IHTTPSession): NanoHTTPD.Response {
                if (session.method == NanoHTTPD.Method.GET) {
                    val mimeType = MimeTypesUtil.determineMimeType(file)
                    return NanoHTTPD.newChunkedResponse(
                        NanoHTTPD.Response.Status.OK,
                        mimeType, assetManager.open(file)
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

    private fun addAssetWebHandlers(
        webHandlerManager: WebHandlerManager,
        assetManager: AssetManager, path: String
    ) {
        try {
            val list = assetManager.list(path)

            if (list == null) {
                return
            }

            if (list.size > 0) {
                for (file in list) {
                    addAssetWebHandlers(webHandlerManager, assetManager, "$path/$file")
                }
            } else {
                webHandlerManager.register(
                    "/$path",
                    newStaticAssetHandler(assetManager, path)
                )
            }
        } catch (e: IOException) {
            Log.w(TAG, e)
        }
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
        addAssetWebHandlers(webHandlerManager, assetManager, "as")
    }
}

