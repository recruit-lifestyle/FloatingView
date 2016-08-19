package jp.co.recruit_lifestyle.sample;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import jp.co.recruit.floatingview.R;
import jp.co.recruit_lifestyle.sample.fragment.DeleteActionFragment;
import jp.co.recruit_lifestyle.sample.service.CustomFloatingViewService;

/**
 * 通知から起動後に削除アクションを行う画面です。
 */
public class DeleteActionActivity extends Activity implements ServiceConnection, DeleteActionFragment.DeleteActionCallback {

    /**
     * デバッグログ用のタグ
     */
    private static final String TAG = "DeleteActionActivity";

    /**
     * 設定フラグメントのタグ
     */
    private static final String FRAGMENT_TAG_DELETE_ACTION = "delete_action";

    /**
     * 削除対象のService
     */
    private Service mTargetService;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_action);

        if (savedInstanceState == null) {
            final FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.add(R.id.container, DeleteActionFragment.newInstance(), FRAGMENT_TAG_DELETE_ACTION);
            ft.commit();
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mTargetService = ((CustomFloatingViewService.CustomFloatingViewServiceBinder) service).getService();

        // バインド直後に即切り
        if (mTargetService != null) {
            unbindService(this);
            mTargetService.stopSelf();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceDisconnected(ComponentName name) {
        mTargetService = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearFloatingView() {
        bindService(new Intent(this, CustomFloatingViewService.class), this, Context.BIND_AUTO_CREATE);
    }
}