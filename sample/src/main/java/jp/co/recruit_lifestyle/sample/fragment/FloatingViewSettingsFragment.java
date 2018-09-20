package jp.co.recruit_lifestyle.sample.fragment;


import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;

import jp.co.recruit.floatingview.R;

/**
 * FloatingViewの設定を行います。
 */
public class FloatingViewSettingsFragment extends PreferenceFragmentCompat {

    /**
     * FloatingViewSettingsFragmentを生成します。
     *
     * @return FloatingViewSettingsFragment
     */
    public static FloatingViewSettingsFragment newInstance() {
        final FloatingViewSettingsFragment fragment = new FloatingViewSettingsFragment();
        return fragment;
    }

    /**
     * コンストラクタ
     */
    public FloatingViewSettingsFragment() {
        // Required empty public constructor
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_floatingview, null);
    }
}
