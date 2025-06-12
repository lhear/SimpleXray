package com.simplexray.an

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainFragmentStateAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {
    val configFragment: ConfigFragment = ConfigFragment()
    val logFragment: LogFragment = LogFragment()
    val settingsFragment: SettingsFragment = SettingsFragment()

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> configFragment
            1 -> logFragment
            2 -> settingsFragment
            else -> Fragment()
        }
    }

    override fun getItemCount(): Int {
        return 3
    }
}
