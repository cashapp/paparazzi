import android.widget.LinearLayout
import com.squareup.cash.screenshot.R
import com.squareup.cash.screenshot.jvm.Screenshotter
import org.junit.Rule
import org.junit.Test

class BoostViewTest {
    @field:Rule
    val snapshotter = Screenshotter()

    @Test
    fun idle() {
        val boostView: LinearLayout = snapshotter.inflate(R.layout.boost_view)
        snapshotter.snapshot(boostView)
    }
}