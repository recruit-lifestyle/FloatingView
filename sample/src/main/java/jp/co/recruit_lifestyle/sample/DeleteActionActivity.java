package jp.co.recruit_lifestyle.sample;

import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;

import jp.co.recruit.floatingview.R;
import jp.co.recruit_lifestyle.sample.fragment.DeleteActionFragment;

/**
 * 通知から起動後に削除アクションを行う画面です。
 */
public class DeleteActionActivity extends AppCompatActivity {

    /**
     * 設定フラグメントのタグ
     */
    private static final String FRAGMENT_TAG_DELETE_ACTION = "delete_action";

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_action);

        if (savedInstanceState == null) {
            final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.container, DeleteActionFragment.newInstance(), FRAGMENT_TAG_DELETE_ACTION);
            ft.commit();
        }

    }
}