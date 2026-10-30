package hu.szileemester.adamazon

import android.app.Application

class AdamazonApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
