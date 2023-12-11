package tech.ritsesoc.sensorreader.ui.main;

import android.bluetooth.le.ScanSettings;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentStatePagerAdapter;

import tech.ritsesoc.sensorreader.Cam2;
import tech.ritsesoc.sensorreader.FragmentPage1;
import tech.ritsesoc.sensorreader.FragmentPage2;
import tech.ritsesoc.sensorreader.FragmentPage3;
import tech.ritsesoc.sensorreader.R;

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
public class SectionsPagerAdapter extends FragmentStatePagerAdapter {

    @StringRes
//    private static final int[] TAB_TITLES = new int[]{R.string.tab_text_3, R.string.tab_text_2,R.string.tab_text_1,R.string.tab_text_4};
    private static final int[] TAB_TITLES = new int[]{R.string.tab_text_2,R.string.tab_text_1,R.string.tab_text_4};
    private final Context mContext;

    public SectionsPagerAdapter(Context context, FragmentManager fm) {
        super(fm);
        mContext = context;
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        Fragment fragment = null;
        switch(position){
            case 0:
                fragment =new FragmentPage2();
                break;
            case 1:
                fragment = new FragmentPage1();
                break;
            case 2:
                fragment = new Cam2();
                break;
        }
        return fragment;
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return mContext.getResources().getString(TAB_TITLES[position]);
    }

    @Override
    public int getCount() {
        // Show 3 total pages.
        return 3;
    }
}