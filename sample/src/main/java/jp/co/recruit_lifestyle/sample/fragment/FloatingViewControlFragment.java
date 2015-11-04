package jp.co.recruit_lifestyle.sample.fragment;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import jp.co.recruit.floatingview.R;
import jp.co.recruit_lifestyle.sample.service.ChatHeadService;
import jp.co.recruit_lifestyle.sample.service.FloatingAdService;


/**
 * 設定画面のフラグメントです。
 */
public class FloatingViewControlFragment extends Fragment {

    /**
     * デバッグログ用のタグ
     */
    private static final String TAG = "FloatingViewControl";

    /**
     * CountDownTimer
     */
    private CountDownTimer mTimer;

    /**
     * シンプルなFloatingViewを表示するフローのパーミッション許可コード
     */
    private static final int CHATHEAD_OVERLAY_PERMISSION_REQUEST_CODE = 100;

    /**
     * 広告連携のFloatingViewを表示するフローのパーミッション許可コード
     */
    private static final int FLOATINGAD_OVERLAY_PERMISSION_REQUEST_CODE = 101;

    /**
     * FloatingViewControlFragmentを生成します。
     */
    public static FloatingViewControlFragment newInstance() {
        FloatingViewControlFragment fragment = new FloatingViewControlFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * コンストラクタ
     */
    public FloatingViewControlFragment() {
        // Required empty public constructor
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTimer = new CountDownTimer(3000L, 3000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                // do nothing
            }

            @Override
            public void onFinish() {
                final Context context = getActivity();
                final boolean canShow = showFloatingAd(context);
                if (!canShow) {
                    // 広告トリガーのFloatingViewの表示許可設定
                    @SuppressLint("InlinedApi")
                    final Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.getPackageName()));
                    startActivityForResult(intent, FLOATINGAD_OVERLAY_PERMISSION_REQUEST_CODE);
                }
            }
        };

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_floating_view_control, container, false);
        // デモの表示
        rootView.findViewById(R.id.showDemo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Context context = getActivity();
                final boolean canShow = showChatHead(context);
                if (!canShow) {
                    // シンプルなFloatingViewの表示許可設定
                    @SuppressLint("InlinedApi")
                    final Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.getPackageName()));
                    startActivityForResult(intent, CHATHEAD_OVERLAY_PERMISSION_REQUEST_CODE);
                }
            }
        });
        // 広告の表示
        rootView.findViewById(R.id.showAd).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTimer.start();
                Log.d(TAG, getString(R.string.ad_click_message));
            }
        });
        return rootView;
    }

    /**
     * オーバレイ表示の許可を処理します。
     */
    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CHATHEAD_OVERLAY_PERMISSION_REQUEST_CODE) {
            final Context context = getActivity();
            final boolean canShow = showChatHead(context);
            if (!canShow) {
                Log.w(TAG, getString(R.string.permission_denied));
            }
        } else if (requestCode == FLOATINGAD_OVERLAY_PERMISSION_REQUEST_CODE) {
            final Context context = getActivity();
            final boolean canShow = showFloatingAd(context);
            if (!canShow) {
                Log.w(TAG, getString(R.string.permission_denied));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        if (mTimer != null) {
            mTimer.cancel();
        }
        super.onDestroy();
    }

    /**
     * シンプルなFloatingViewの表示
     *
     * @param context Context
     * @return 表示できる場合はtrue, 表示できない場合はfalse
     */
    @SuppressLint("NewApi")
    private boolean showChatHead(Context context) {
        // API22以下かチェック
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            context.startService(new Intent(context, ChatHeadService.class));
            return true;
        }

        // 他のアプリの上に表示できるかチェック
        if (Settings.canDrawOverlays(context)) {
            context.startService(new Intent(context, ChatHeadService.class));
            return true;
        }

        return false;
    }

    /**
     * 広告表示のFloatingViewの表示
     *
     * @param context Context
     * @return 表示できる場合はtrue, 表示できない場合はfalse
     */
    @SuppressLint("NewApi")
    private boolean showFloatingAd(Context context) {
        // API22以下かチェック
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            context.startService(new Intent(context, FloatingAdService.class));
            return true;
        }

        // 他のアプリの上に表示できるかチェック
        if (Settings.canDrawOverlays(context)) {
            context.startService(new Intent(context, FloatingAdService.class));
            return true;
        }

        return false;
    }
}
