package jp.co.recruit_lifestyle.sample.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import jp.co.recruit.floatingview.R;


/**
 * FloatingViewのサンプルサービスを削除するフラグメントです。
 */
public class DeleteActionFragment extends Fragment {

    /**
     * DeleteActionCallback
     */
    private DeleteActionCallback mDeleteActionCallback;

    /**
     * DeleteActionFragmentを生成します。
     *
     * @return DeleteActionFragment
     */
    public static DeleteActionFragment newInstance() {
        final DeleteActionFragment fragment = new DeleteActionFragment();
        return fragment;
    }

    /**
     * コンストラクタ
     */
    public DeleteActionFragment() {
        // Required empty public constructor
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            onAttachFragment(activity);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        onAttachFragment(context);
    }

    /**
     * onAttach(Activity)のbackport用メソッド
     * INFO:support v13を使用しているため現在はこの方法しかできない
     *
     * @param context Context
     */
    private void onAttachFragment(Context context) {
        try {
            mDeleteActionCallback = (DeleteActionCallback) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement " + DeleteActionCallback.class.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_delete_action, container, false);
        // 削除ボタン
        final View clearFloatingButton = rootView.findViewById(R.id.clearDemo);
        clearFloatingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDeleteActionCallback.clearFloatingView();
            }
        });
        return rootView;
    }

    /**
     * 設定アクションのコールバックです。
     */
    public interface DeleteActionCallback {

        /**
         * デモ表示を削除します。
         */
        void clearFloatingView();

    }
}
