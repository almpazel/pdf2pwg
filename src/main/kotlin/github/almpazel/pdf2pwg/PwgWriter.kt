package github.almpazel.pdf2pwg
/**
 * All elements of a PWG Raster header as described in section 4.3 of
 * [PWG-5102.4](https://ftp.pwg.org/pub/pwg/candidates/cs-ippraster10-20120420-5102.4.pdf).
 */
import java.io.*
import com.hp.jipp.model.MediaSource
import com.hp.jipp.model.Orientation
import com.hp.jipp.model.PrintQuality
import com.hp.jipp.model.PwgRasterDocumentSheetBack
import com.hp.jipp.model.Sides
import java.util.*

class PwgWriter(private val settings: OutputSettings)  {

    lateinit var stream: DataOutputStream

    fun init(out:File) {
        stream= DataOutputStream(BufferedOutputStream(FileOutputStream(out)))
        stream.write("RaS2".toByteArray())
    }

    fun writePage(page: RenderablePage) {
        page.init()
        page.header.write(stream)

        // Pack and write the content bytes
        var yOffset = 0
        var byteArray: ByteArray? = null
        while (yOffset < page.heightPixels) {
            val height = Math.min(MAX_SWATH_HEIGHT, page.heightPixels - yOffset)
            val renderSize = page.renderSize(height, settings.colorSpace)
            if (byteArray?.size != renderSize) {
                byteArray = ByteArray(renderSize)
            }
            page.render(yOffset, height, settings.colorSpace, byteArray)
//
            val encodedBytes = ByteArrayOutputStream()
            page.header.packBits.encode(ByteArrayInputStream(byteArray), encodedBytes)
            stream.write(encodedBytes.toByteArray())
            yOffset += height
        }
    }

    fun close() {
        stream.close()
    }


    companion object {
        // Pack and encode only this many lines at a time to conserve RAM
        const val MAX_SWATH_HEIGHT = 256
    }
}

/** Identifies a color space which describes how each pixel of image data is encoded */
@Suppress("MagicNumber")
enum class ColorSpace(val bytesPerPixel: Int) {
    /** Three bytes per pixel: Red, Green, Blue */
    Rgb(3),
    /** One byte per pixel, between 0x00=Black and 0xFF=White */
    Grayscale(1);
}

data class PwgHeader(
        val mediaColor: String = "",
        val mediaType: String = "",
        val printContentOptimize: String = "",
        val cutMedia: When = When.Never,
        /** True if printing two-sided. */
        val duplex: Boolean = false,
        val hwResolutionX: Int,
        val hwResolutionY: Int,
        val insertSheet: Boolean = false,
        val jog: When = When.Never,
        val leadingEdge: Edge = Edge.ShortEdgeFirst,
        val mediaPosition: MediaPosition = MediaPosition.Auto,
        val mediaWeightMetric: Int = 0,
        val numCopies: Int = 0,
        val orientation: Orientation = Orientation.Portrait,
        /** Media width in points. */
        val pageSizeX: Int = 0,
        /** Media height in points. */
        val pageSizeY: Int = 0,
        /** True if two-sided printing should be flipped along the short-edge. */
        val tumble: Boolean = false,
        /** Full-bleed page width in pixels. */
        val width: Int,
        /** Full-bleed page height in pixels. */
        val height: Int,
        val bitsPerColor: Int,
        val bitsPerPixel: Int,
        val colorOrder: ColorOrder = ColorOrder.Chunky,
        val colorSpace: ColorSpace,
        val totalPageCount: Int = 0,
        val crossFeedTransform: Int = 1,
        val feedTransform: Int = 1,
        /** Left position of non-blank area in pixels, if image box is known. */
        val imageBoxLeft: Int = 0,
        /** Top position of non-blank area in pixels, if image box is known. */
        val imageBoxTop: Int = 0,
        /** Right position of non-blank area in pixels, if image box is known. */
        val imageBoxRight: Int = 0,
        /** Bottom position of non-blank area in pixels, if image box is known. */
        val imageBoxBottom: Int = 0,
        /** An sRGB color field containing 24 bits of color data. Default: WHITE */
        val alternatePrimary: Int = WHITE,
        val printQuality: PrintQuality = PrintQuality.Default,
        /** USB vendor identification number or 0. */
        val vendorIdentifier: Int = 0,
        /** Octets containing 0-1088 bytes of vendor-specific data. */
        val vendorData: ByteArray = byteArrayOf(),
        val renderingIntent: String = "",
        val pageSizeName: String = ""
) {
    /** Number of bytes per line, always based on [width] and [bitsPerPixel]. */
    val bytesPerLine: Int = ((bitsPerPixel * width + 7) / 8)
    val cupsCompression: Int = 0
    val cupsRowCount: Int = 0
    val cupsRowFeed: Int = 0
    val cupsRowStep: Int = 0

    /** Number of colors supported. */
    val numColors: Int = bitsPerPixel / bitsPerColor

    /** PackBits object used for encoding/decoding pixels of data. */
    val packBits = PackBits(
            bytesPerPixel = bitsPerPixel / PwgSettings.BITS_PER_BYTE,
            pixelsPerLine = width
    )

    init {
        if (vendorData.size > MAX_VENDOR_DATA_SIZE) {
            throw IllegalArgumentException("vendorData.size of ${vendorData.size} must not be > $MAX_VENDOR_DATA_SIZE")
        }
    }

    /** Something that has an integer value. */
    interface HasValue {
        val value: Int
    }

    /** Converts from an integer value to a T. */
    interface ValueConverter<T : HasValue> {
        fun from(value: Int): T
    }

    /** Points during print when another operation should take place. */
    enum class When(override val value: Int) : HasValue {
        Never(0), AfterDocument(1), AfterJob(2), AfterSet(3), AfterPage(4);

        companion object :
                ValueConverter<When> {
            override fun from(value: Int) = values().firstOrNull { it.value == value } ?: Never
        }
    }

    /** Kinds of duplexing. */
    enum class Edge(override val value: Int) : HasValue {
        ShortEdgeFirst(0), LongEdgeFirst(1);

        companion object :
                ValueConverter<Edge> {
            override fun from(value: Int) = values().firstOrNull { it.value == value } ?: ShortEdgeFirst
        }
    }

    /** Output orientation of a page. */
    enum class Orientation(override val value: Int) : HasValue {
        Portrait(0), Landscape(1), ReversePortrait(2), ReverseLandscape(3);

        companion object :
                ValueConverter<Orientation> {
            override fun from(value: Int) = values().firstOrNull { it.value == value } ?: Portrait
        }
    }

    enum class ColorOrder(override val value: Int) : HasValue {
        Chunky(0);

        companion object :
                ValueConverter<ColorOrder> {
            override fun from(value: Int) = values().firstOrNull { it.value == value } ?: Chunky
        }
    }

    /** Meaning of color values provided for each pixel. */
    enum class ColorSpace(override val value: Int) : HasValue {
        Rgb(1), Black(3), Cmyk(6), Sgray(18), Srgb(19), AdobeRgb(20), Device1(48), Device2(49), Device3(50),
        Device4(51), Device5(52), Device6(53), Device7(54), Device8(55), Device9(56), Device10(57), Device11(58),
        Device12(59), Device13(60), Device14(61), Device15(62);

        companion object :
                ValueConverter<ColorSpace> {
            override fun from(value: Int) = values().firstOrNull { it.value == value } ?: Srgb

            /** Given a [com.hp.jipp.pdl.ColorSpace] return the corresponding [PwgHeader.ColorSpace]. */
            fun from(from: github.almpazel.pdf2pwg.ColorSpace) =
                    when (from) {
                        github.almpazel.pdf2pwg.ColorSpace.Rgb -> Srgb
                        github.almpazel.pdf2pwg.ColorSpace.Grayscale -> Sgray
                    }
        }
    }

    /** Media input source. */
    enum class MediaPosition(override val value: Int) : HasValue {
        Auto(0), Main(1), Alternate(2), LargeCapacity(3), Manual(4), Envelope(5), Disc(6), Photo(7), Hagaki(8),
        MainRoll(9), AlternateRoll(10), Top(11), Middle(12), Bottom(13), Side(14), Left(15), Right(16), Center(17),
        Rear(18), ByPassTray(19), Tray1(20), Tray2(21), Tray3(22), Tray4(23), Tray5(24), Tray6(25), Tray7(26),
        Tray8(27), Tray9(28), Tray10(29), Tray11(30), Tray12(31), Tray13(32), Tray14(33), Tray15(34), Tray16(35),
        Tray17(36), Tray18(37), Tray19(38), Tray20(39), Roll1(40), Roll2(41), Roll3(42), Roll4(43), Roll5(44),
        Roll6(45), Roll7(46), Roll8(47), Roll9(48), Roll10(49);

        companion object :
                ValueConverter<MediaPosition> {
            override fun from(value: Int) = values().firstOrNull { it.value == value } ?: Auto
        }
    }

    /** Requested output quality. */
    enum class PrintQuality(override val value: Int) : HasValue {
        Default(0), Draft(3), Normal(4), High(5);

        companion object :
                ValueConverter<PrintQuality> {
            override fun from(value: Int) = values().firstOrNull { it.value == value } ?: Default
        }
    }

    /** Writes a PWG header containing exactly 1796 octets. */
    fun write(output: OutputStream) {
        ((output as? DataOutputStream) ?: DataOutputStream(output)).apply {
            writeCString(PWG_RASTER_NAME) // Always the same version 1
            writeCString(mediaColor)
            writeCString(mediaType)
            writeCString(printContentOptimize)
            writeReserved(12)
            writeInt(cutMedia)
            writeInt(duplex)
            writeInt(hwResolutionX)
            writeInt(hwResolutionY)
            writeReserved(16)
            writeInt(insertSheet)
            writeInt(jog)
            writeInt(leadingEdge)
            writeReserved(12)
            writeInt(mediaPosition)
            writeInt(mediaWeightMetric)
            writeReserved(8)
            writeInt(numCopies)
            writeInt(orientation)
            writeReserved(4)
            writeInt(pageSizeX)
            writeInt(pageSizeY)
            writeReserved(8)
            writeInt(tumble)
            writeInt(width)
            writeInt(height)
            writeReserved(4)
            writeInt(bitsPerColor)
            writeInt(bitsPerPixel)
            writeInt(bytesPerLine)
            writeInt(colorOrder)
            writeInt(colorSpace)
            writeReserved(16)
            writeInt(numColors) //version 2 starts
            writeReserved(28)
            writeInt(totalPageCount)
            writeInt(crossFeedTransform)
            writeInt(feedTransform)
            writeInt(imageBoxLeft)
            writeInt(imageBoxTop)
            writeInt(imageBoxRight)
            writeInt(imageBoxBottom)
            writeInt(alternatePrimary)
            writeInt(printQuality)
            writeReserved(20)
            writeInt(vendorIdentifier)
            writeInt(vendorData.size)
            write(vendorData, 0, vendorData.size)
            // Pad with 0
            writeReserved(MAX_VENDOR_DATA_SIZE - vendorData.size)
            writeReserved(64)
            writeCString(renderingIntent)
            writeCString(pageSizeName)
        }
    }



    companion object {
        const val PWG_RASTER_NAME = "PwgRaster"
        const val MAX_VENDOR_DATA_SIZE = 1088
        const val HEADER_SIZE = 1796
        const val WHITE = 0xFFFFFF
        private const val CSTRING_LENGTH = 64

        /**
         * Write 0-bytes into the output string, [bytes] long.
         */
        private fun DataOutputStream.writeReserved(bytes: Int) {
            write(ByteArray(bytes))
        }

        /**
         * Write the specified string, up to [width] bytes, zero-padded to exactly [width].
         */
        private fun DataOutputStream.writeCString(string: String) {
            val bytes = string.toByteArray()
            write(bytes, 0, Math.min(CSTRING_LENGTH, bytes.size))
            writeReserved(CSTRING_LENGTH - bytes.size)
        }

        /**
         * Write an enum value object.
         */
        private fun DataOutputStream.writeInt(hasValue: HasValue) {
            writeInt(hasValue.value)
        }

        /**
         * A regular writeBoolean only writes one byte. But PWG is very thorough and uses four bytes to encode a
         * single true/false bit.
         */
        private fun DataOutputStream.writeInt(value: Boolean) {
            writeInt(if (value) 1 else 0)
        }

    }
}



data class PwgSettings @JvmOverloads constructor(
        val output: OutputSettings = OutputSettings(pageCount = 2),
        val sheetBack: String = PwgRasterDocumentSheetBack.normal,
        val orientation: Orientation = Orientation.portrait
) {
    val pwgMediaPosition = output.source.toPwgMediaPosition()

    val pwgColorSpace = PwgHeader.ColorSpace.from(output.colorSpace)

    val pwgPrintQuality = output.quality?.toPwgPrintQuality() ?: PwgHeader.PrintQuality.Default

    fun buildHeader(page: RenderablePage): PwgHeader {
        return PwgHeader(
                hwResolutionX = output.dpi,
                hwResolutionY = output.dpi,
                orientation = orientation.toPwgOrientation(),
                pageSizeX = page.widthPixels * POINTS_PER_INCH / output.dpi,
                pageSizeY = page.heightPixels * POINTS_PER_INCH / output.dpi,
                width = page.widthPixels,
                height = page.heightPixels,
                bitsPerColor = BITS_PER_BYTE,
                bitsPerPixel = output.colorSpace.bytesPerPixel * BITS_PER_BYTE,
                colorSpace = pwgColorSpace,
                duplex = output.sides != Sides.oneSided,
                tumble = output.sides == Sides.twoSidedShortEdge,
                mediaPosition = pwgMediaPosition,
                printQuality = pwgPrintQuality,
                feedTransform = 1,
                crossFeedTransform = 1,
                totalPageCount = output.pageCount,
                numCopies = output.pageCopies
        )
    }

    companion object {
        private const val POINTS_PER_INCH = 72
        const val BITS_PER_BYTE = 8

        private fun Orientation.toPwgOrientation(): PwgHeader.Orientation =
                when (this) {
                    Orientation.portrait -> PwgHeader.Orientation.Portrait
                    Orientation.reversePortrait -> PwgHeader.Orientation.ReversePortrait
                    Orientation.landscape -> PwgHeader.Orientation.Landscape
                    Orientation.reverseLandscape -> PwgHeader.Orientation.ReverseLandscape
                    else -> PwgHeader.Orientation.Portrait
                }

        @Suppress("ComplexMethod")
        private fun String.toPwgMediaPosition() =
                when (this) {
                    MediaSource.alternate -> PwgHeader.MediaPosition.Alternate
                    MediaSource.alternateRoll -> PwgHeader.MediaPosition.AlternateRoll
                    MediaSource.auto -> PwgHeader.MediaPosition.Auto
                    MediaSource.bottom -> PwgHeader.MediaPosition.Bottom
                    MediaSource.byPassTray -> PwgHeader.MediaPosition.ByPassTray
                    MediaSource.center -> PwgHeader.MediaPosition.Center
                    MediaSource.disc -> PwgHeader.MediaPosition.Disc
                    MediaSource.envelope -> PwgHeader.MediaPosition.Envelope
                    MediaSource.hagaki -> PwgHeader.MediaPosition.Hagaki
                    MediaSource.largeCapacity -> PwgHeader.MediaPosition.LargeCapacity
                    MediaSource.left -> PwgHeader.MediaPosition.Left
                    MediaSource.main -> PwgHeader.MediaPosition.Main
                    MediaSource.mainRoll -> PwgHeader.MediaPosition.MainRoll
                    MediaSource.manual -> PwgHeader.MediaPosition.Manual
                    MediaSource.middle -> PwgHeader.MediaPosition.Middle
                    MediaSource.photo -> PwgHeader.MediaPosition.Photo
                    MediaSource.rear -> PwgHeader.MediaPosition.Rear
                    MediaSource.right -> PwgHeader.MediaPosition.Right
                    MediaSource.roll1 -> PwgHeader.MediaPosition.Roll1
                    MediaSource.roll10 -> PwgHeader.MediaPosition.Roll10
                    MediaSource.roll2 -> PwgHeader.MediaPosition.Roll2
                    MediaSource.roll3 -> PwgHeader.MediaPosition.Roll3
                    MediaSource.roll4 -> PwgHeader.MediaPosition.Roll4
                    MediaSource.roll5 -> PwgHeader.MediaPosition.Roll5
                    MediaSource.roll6 -> PwgHeader.MediaPosition.Roll6
                    MediaSource.roll7 -> PwgHeader.MediaPosition.Roll7
                    MediaSource.roll8 -> PwgHeader.MediaPosition.Roll8
                    MediaSource.roll9 -> PwgHeader.MediaPosition.Roll9
                    MediaSource.side -> PwgHeader.MediaPosition.Side
                    MediaSource.top -> PwgHeader.MediaPosition.Top
                    MediaSource.tray1 -> PwgHeader.MediaPosition.Tray1
                    MediaSource.tray10 -> PwgHeader.MediaPosition.Tray10
                    MediaSource.tray11 -> PwgHeader.MediaPosition.Tray11
                    MediaSource.tray12 -> PwgHeader.MediaPosition.Tray12
                    MediaSource.tray13 -> PwgHeader.MediaPosition.Tray13
                    MediaSource.tray14 -> PwgHeader.MediaPosition.Tray14
                    MediaSource.tray15 -> PwgHeader.MediaPosition.Tray15
                    MediaSource.tray16 -> PwgHeader.MediaPosition.Tray16
                    MediaSource.tray17 -> PwgHeader.MediaPosition.Tray17
                    MediaSource.tray18 -> PwgHeader.MediaPosition.Tray18
                    MediaSource.tray19 -> PwgHeader.MediaPosition.Tray19
                    MediaSource.tray2 -> PwgHeader.MediaPosition.Tray2
                    MediaSource.tray20 -> PwgHeader.MediaPosition.Tray20
                    MediaSource.tray3 -> PwgHeader.MediaPosition.Tray3
                    MediaSource.tray4 -> PwgHeader.MediaPosition.Tray4
                    MediaSource.tray5 -> PwgHeader.MediaPosition.Tray5
                    MediaSource.tray6 -> PwgHeader.MediaPosition.Tray6
                    MediaSource.tray7 -> PwgHeader.MediaPosition.Tray7
                    MediaSource.tray8 -> PwgHeader.MediaPosition.Tray8
                    MediaSource.tray9 -> PwgHeader.MediaPosition.Tray9
                    else -> throw IllegalArgumentException("$this is not a recognized media source type")
                }

        private fun PrintQuality.toPwgPrintQuality() =
                when (this) {
                    PrintQuality.draft -> PwgHeader.PrintQuality.Draft
                    PrintQuality.normal -> PwgHeader.PrintQuality.Normal
                    PrintQuality.high -> PwgHeader.PrintQuality.High
                    else -> null
                }
    }
}


/**
 * Encoder/Decoder for the PackBits algorithm described in the Wi-Fi Peer-to-Peer Services Print Technical
 * Specification v1.1
 */
class PackBits(
        /** Number of bytes per pixel (1 for grayscale, 3 for RGB) */
        private val bytesPerPixel: Int,
        /** Total number of pixels on each horizontal line */
        private val pixelsPerLine: Int
) {

    /** Reads [inputPixels] until there are no more, writing encoded bytes to [outputBytes] */
    fun encode(inputPixels: InputStream, outputBytes: OutputStream) {
        EncodeContext(
                inputPixels,
                outputBytes,
                bytesPerPixel,
                pixelsPerLine
        ).encode()
    }

    /** Manage the mutable context during encoding */
    private class EncodeContext(
            private val pixelsIn: InputStream,
            private val bytesOut: OutputStream,
            private val bytesPerPixel: Int,
            pixelsPerLine: Int
    ) {
        private val bytesPerLine = bytesPerPixel * pixelsPerLine
        private var lineArrayValid = false
        private var lineArray = ByteArray(bytesPerLine)
        private var nextLineArrayValid = false
        private var nextLineArray = ByteArray(bytesPerLine)
        private var lineRepeatCount = 0
        private var bytePos: Int = 0
        private var pixelCount: Int = 0

        /** Consume the next event from the current encoding context */
        fun encode() {
            while (readNextLine()) {
                bytesOut.write(lineRepeatCount - 1)
                encodePixelGroups()
            }
        }

        private fun encodePixelGroups() {
            bytePos = 0
            while (bytePos < lineArray.size) {
                if (bytePos + bytesPerPixel == lineArray.size) {
                    // Exactly one pixel left so encode it as repeating of 1
                    pixelCount = 1
                    encodeRepeatingPixelGroup()
                } else if (lineArray.equals(bytePos, bytesPerPixel, lineArray, bytePos + bytesPerPixel)) {
                    pixelCount = 2
                    seekNonMatchingPixel()
                    encodeRepeatingPixelGroup()
                } else {
                    // Non-repeating pixels, seek the first two matching pixels at end
                    pixelCount = 2
                    seekMatchingPixels()
                    if (pixelCount == 1) {
                        encodeRepeatingPixelGroup()
                    } else {
                        encodeNonRepeatingPixelGroup()
                    }
                }
            }
        }

        private fun seekNonMatchingPixel() {
            // Multiple repeating pixels, seek EOL or non-matching pixel
            var nextPixelIndex = bytePos + pixelCount * bytesPerPixel
            while (pixelCount < MAX_GROUP &&
                    nextPixelIndex < lineArray.size &&
                    lineArray.equals(bytePos, bytesPerPixel, lineArray, nextPixelIndex)) {
                pixelCount++
                nextPixelIndex += bytesPerPixel
            }
        }

        private fun seekMatchingPixels() {
            var nextPixelIndex = bytePos + pixelCount * bytesPerPixel
            while (nextPixelIndex < lineArray.size && pixelCount < MAX_GROUP) {
                if (lineArray.equals(nextPixelIndex - bytesPerPixel, bytesPerPixel,
                                lineArray, nextPixelIndex)) {
                    // We found two matching pixels so back up
                    pixelCount--
                    break
                } else {
                    pixelCount++
                    nextPixelIndex += bytesPerPixel
                }
            }
        }

        private fun encodeRepeatingPixelGroup() {
            bytesOut.write(pixelCount - 1)
            bytesOut.write(lineArray, bytePos, bytesPerPixel)
            bytePos += pixelCount * bytesPerPixel
        }

        private fun encodeNonRepeatingPixelGroup() {
            bytesOut.write(NON_REPEAT_SUBTRACT_FROM - pixelCount)
            bytesOut.write(lineArray, bytePos, bytesPerPixel * pixelCount)
            bytePos += pixelCount * bytesPerPixel
        }

        /** Compare a section of this ByteArray with a section of the same length in another byte array */
        private fun ByteArray.equals(offset: Int, length: Int, other: ByteArray, otherOffset: Int): Boolean {
            for (index in 0 until length) {
                if (this[offset + index] != other[otherOffset + index]) return false
            }
            return true
        }

        private fun readNextLine(): Boolean {
            lineArrayValid = false

            // Take the next line if we can
            if (nextLineArrayValid) {
                val swap = lineArray
                lineArray = nextLineArray
                nextLineArray = swap
                nextLineArrayValid = false
                lineArrayValid = true
            }

            // Read a new line if we need it
            if (!lineArrayValid) {
                if (!readLine(lineArray)) return false
            }

            // Now read additional lines beyond lineIn to see if there are repeats
            lineRepeatCount = 1
            while (lineRepeatCount <= MAX_LINE_REPEAT) {
                if (readLine(nextLineArray)) {
                    if (Arrays.equals(lineArray, nextLineArray)) {
                        lineRepeatCount++
                    } else {
                        // We found a different line so hold for later
                        nextLineArrayValid = true
                        break
                    }
                } else {
                    // No more lines to read
                    nextLineArrayValid = false
                    break
                }
            }
            return true
        }

        private fun readLine(into: ByteArray): Boolean {
            return when (val bytesRead = pixelsIn.read(into)) {
                -1 -> false
                into.size -> true
                else -> throw IOException("Could not read whole line ($bytesRead bytes instead of ${lineArray.size}")
            }
        }
    }

    companion object {
        private const val MAX_GROUP = 128
        private const val MAX_LINE_REPEAT = PwgWriter.MAX_SWATH_HEIGHT
        private const val NON_REPEAT_SUBTRACT_FROM = 257
    }
}


data class OutputSettings(
        val colorSpace: ColorSpace = ColorSpace.Rgb,
        val sides: String = Sides.oneSided,
        val dpi: Int = 300,
        val source: String = MediaSource.auto,
        val quality: PrintQuality? = null,
        val reversed: Boolean = false,
        var pageCount: Int=0,
        val pageCopies: Int=0
)
