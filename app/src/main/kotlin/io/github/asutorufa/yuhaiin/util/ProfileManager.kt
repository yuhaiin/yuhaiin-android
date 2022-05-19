package io.github.asutorufa.yuhaiin.util

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import io.github.asutorufa.yuhaiin.R

class ProfileManager(private val mContext: Context) {
    private val mPref: SharedPreferences = mContext.getSharedPreferences(Constants.PREF, Context.MODE_PRIVATE)

    private val profileList: MutableList<String>
        get() {
            val mProfiles: MutableList<String> = ArrayList()
            mProfiles.add(mContext.getString(R.string.prof_default))
            val profiles = mPref.getString(Constants.PREF_PROFILE, "")!!
                .split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (p in profiles) {
                if (!TextUtils.isEmpty(p)) {
                    mProfiles.add(p)
                }
            }
            return mProfiles
        }
    val profiles: Array<String>
        get() = profileList.toTypedArray()

    fun getProfile(name: String): Profile? {
        return if (!profileList.contains(name)) {
            null
        } else {
            Profile(mPref, name)
        }
    }

    val default: Profile
        get() = Profile(mPref, mPref.getString(Constants.PREF_LAST_PROFILE, profileList[0])!!)

    fun switchDefault(name: String) {
        if (name in profileList) mPref.edit().putString(Constants.PREF_LAST_PROFILE, name).apply()
    }

    fun addProfile(name: String): Profile? {
        val mProfiles = profileList
        return if (mProfiles.contains(name)) {
            null
        } else {
            mProfiles.add(name)
            mProfiles.removeAt(0)
            mPref.edit().putString(Constants.PREF_PROFILE, Utility.join(mProfiles, "\n"))
                .putString(Constants.PREF_LAST_PROFILE, name).apply()
            default
        }
    }

    fun removeProfile(name: String): Boolean {
        val mProfiles = profileList
        if (name == mProfiles[0] || !mProfiles.contains(name)) {
            return false
        }
        Profile(mPref, name).delete()
        mProfiles.removeAt(0)
        mProfiles.remove(name)
        mPref.edit().putString(Constants.PREF_PROFILE, Utility.join(mProfiles, "\n"))
            .remove(Constants.PREF_LAST_PROFILE).apply()
        return true
    }
}