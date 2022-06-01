package io.github.asutorufa.yuhaiin.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class ProfileTest {
    private lateinit var db: YuhaiinDatabase


    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = YuhaiinDatabase.getInstance(context)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun rwTest() {
        val dao = db.ProfileDao()

//        dao.addProfile(Profile(name = "Default", httpServerPort = 1081))
//        dao.updateProfile(Profile(name = "Default", appList = HashSet<String>().also {
//            it.add("a")
//            it.add("b")
//        }))
        val pro = dao.getProfileByName("Default")
        println(pro)
        println(dao.getLastProfile())
    }
}
