package github.almpazel.pdf2pwg

import android.graphics.BitmapFactory
import cn.bbys.box.Dpi
import cn.bbys.box.util.timeint
import com.hp.jipp.model.PrintQuality
import com.hp.jipp.model.Sides
import java.io.File

class PWGRender(val file: File) {

    @Throws(Exception::class)
    @Synchronized
    fun render(pageCount: Int,pageFrom: Int = 0, pageTo: Int = 0,dpi:Int=300, onPing: (() -> Unit)? =null):File{
        val renderFile=File("${file.path}.pwg")

        val st = timeint()
        Dpi.e(this, "renderer.start")
        val settings = OutputSettings(
                sides = Sides.twoSidedLongEdge,
                quality = PrintQuality.high,
                dpi = dpi,
                pageCount = pageCount
        )
        val pwgWriter = PwgWriter(settings)

        pwgWriter.init(renderFile)

        for (index in pageFrom..pageTo) {

            onPing?.let { it() }

            Dpi.e(this,"render start[$index]:")
            val pst = timeint()
            val image=BitmapFactory.decodeFile("${file.path}-$index.jpg")
            val pit = timeint()
            Dpi.e(this,"render image[$index]: ${pit - pst}")

            pwgWriter.writePage(RenderablePage(image, settings))

            val pet = timeint()
            Dpi.e(this,"render done[$index]: ${pet - pst}")
        }

        pwgWriter.close()

        Dpi.e(this, "renderer.done  total:${timeint() - st}")
        return renderFile
    }



}



