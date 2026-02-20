package im.zoe.flutter_automate_example

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import im.zoe.flutter_automate.core.ScreenCapture

class MainActivity : FlutterActivity() {
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ScreenCapture.REQUEST_CODE) {
            ScreenCapture.onActivityResult(this, resultCode, data)
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
