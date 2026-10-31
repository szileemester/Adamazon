package hu.szileemester.adamazon

import android.app.Application
import hu.szileemester.adamazon.data.VoiceRecognizer
import java.io.File

class AdamazonApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        VoiceRecognizer.restoreMutedStreams(this)
        // Card photos from the camera are only needed until they are compressed and saved.
        File(cacheDir, "camera").deleteRecursively()
    }
}
