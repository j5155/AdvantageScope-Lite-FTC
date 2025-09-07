package page.j5155.advantagescope

import fi.iki.elonen.NanoHTTPD
import org.firstinspires.ftc.robotserver.internal.webserver.RobotControllerWebHandlers
import org.firstinspires.ftc.robotserver.internal.webserver.RobotWebHandlerManager
import org.zeroturnaround.zip.ZipUtil
import java.io.File


/*
* Asset uploading API:
* Frontend makes a POST request to $WEBROOT/uploadAsset/ with FormData with a property named "file" and the filename
* The uploaded file must be a zip file, but it should be directly what the user uploads.
* It can be a zip file directly containing model files, a zip file containing a folder containing model files,
* a zip file containing other zip files containing model files, or a zip file containing multiple folders containing model files.
* The backend must parse all of these conditions.
*/

class AssetUpload(val destinationDir: File): RobotControllerWebHandlers.FileUpload() {

    val TAG = "AssetUpload"

    override fun hook(uploadedFile: File): NanoHTTPD.Response {
        if (uploadedFile.extension.lowercase() != "zip") {
            uploadedFile.delete()
            return RobotWebHandlerManager.clientBadRequestError(TAG, "Not a zip file!")
        }
        val tempDir = File(destinationDir.absolutePath + "/TEMP/")
        val outputDir = File(tempDir.absolutePath + "/" + uploadedFile.nameWithoutExtension)
        ZipUtil.unpack(uploadedFile,outputDir)
        uploadedFile.delete()
        outputDir.walk().forEach {
            if (it.extension.lowercase() == "zip") {
                ZipUtil.unpack(it,File(it.absolutePath + it.nameWithoutExtension))
                it.delete()
            }
        }
        outputDir.walk().forEach {
            if (it.name == "config.json") {
                val folder = File(it.parent ?: return RobotWebHandlerManager.internalErrorResponse(TAG, "config.json has no parent?!"))
                folder.copyRecursively(File("$destinationDir/${folder.name}"),true)
            }
        }
        tempDir.deleteRecursively()


        return RobotWebHandlerManager.OK_RESPONSE
    }

    override fun provideDestinationDirectory(
        fileName: String,
        tempFile: File
    ): File {
        return destinationDir
    }
}