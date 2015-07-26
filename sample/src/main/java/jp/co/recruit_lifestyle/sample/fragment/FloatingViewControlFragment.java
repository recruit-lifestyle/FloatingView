package jp.co.recruit_lifestyle.sample.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import jp.co.recruit.floatingview.R;
import jp.co.recruit_lifestyle.sample.service.ChatHeadService;
import jp.co.recruit_lifestyle.sample.service.FloatingAdService;


/**
 * 設定画面のフラグメントです。
 */
public class FloatingViewControlFragment extends Fragment {

    /**
     * CountDownTimer
     */
    private CountDownTimer mTimer;

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
                final Activity activity = getActivity();
                activity.startService(new Intent(activity, FloatingAdService.class));
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
                final Activity activity = getActivity();
                activity.startService(new Intent(activity, ChatHeadService.class));
            }
        });
        // 広告の表示
        rootView.findViewById(R.id.showAd).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTimer.start();
                Toast.makeText(getActivity(), R.string.ad_click_message, Toast.LENGTH_SHORT).show();
            }
        });
        return rootView;
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
}
