package github.almpazel.pdf2pwg

import android.graphics.Bitmap
import android.graphics.Color

class RenderablePage(val image: Bitmap, val settings: OutputSettings) {

    val widthPixels: Int=image.width
    val heightPixels: Int=image.height

    lateinit var header: PwgHeader

    fun renderSize(swathHeight: Int, colorSpace: ColorSpace): Int =
            widthPixels * colorSpace.bytesPerPixel * swathHeight


    private val redC = 0.2126
    private val greenC = 0.7512
    private val blueC = 0.0722

    fun render(yOffset: Int, swathHeight: Int, colorSpace: ColorSpace, byteArray: ByteArray) {

        var pixel: Int
        var byteIndex = 0
        for (y in yOffset until yOffset + swathHeight) {
            for (x in 0 until image.width) {
                pixel = image.getPixel(x, y)
                val red= Color.red(pixel)
                val green= Color.green(pixel)
                val blue= Color.blue(pixel)

                if (colorSpace == ColorSpace.Grayscale) {
                    byteArray[byteIndex++] = (redC * red + greenC * green + blueC * blue).toByte()
                } else {
                    byteArray[byteIndex++] = red.toByte()
                    byteArray[byteIndex++] = green.toByte()
                    byteArray[byteIndex++] = blue.toByte()
                }
            }
        }
    }

    fun init() {
        header = PwgSettings(settings).buildHeader(this)
    }
}
