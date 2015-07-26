package jp.co.recruit_lifestyle.sample;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.os.Bundle;

import jp.co.recruit.floatingview.R;
import jp.co.recruit_lifestyle.sample.fragment.FloatingViewControlFragment;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.add(R.id.container, FloatingViewControlFragment.newInstance());
            ft.commit();
        }
    }
}
