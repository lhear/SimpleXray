package com.simplexray.an;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class MainFragmentStateAdapter extends FragmentStateAdapter {

    private final ConfigFragment configFragment;
    private final LogFragment logFragment;
    private final SettingsFragment settingsFragment;

    public MainFragmentStateAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        configFragment = new ConfigFragment();
        logFragment = new LogFragment();
        settingsFragment = new SettingsFragment();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return configFragment;
            case 1:
                return logFragment;
            case 2:
                return settingsFragment;
            default:
                return new Fragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }

    public ConfigFragment getConfigFragment() {
        return configFragment;
    }

    public LogFragment getLogFragment() {
        return logFragment;
    }

    public SettingsFragment getSettingsFragment() {
        return settingsFragment;
    }
}
