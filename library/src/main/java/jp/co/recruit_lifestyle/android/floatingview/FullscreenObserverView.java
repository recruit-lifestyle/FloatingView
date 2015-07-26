/**
 * Copyright 2015 RECRUIT LIFESTYLE CO., LTD.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *            http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.recruit_lifestyle.android.floatingview;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

/**
 * フルスクリーンを監視するViewです。
 * http://stackoverflow.com/questions/18551135/receiving-hidden-status-bar-entering-a-full-screen-activity-event-on-a-service/19201933#19201933
 */
class FullscreenObserverView extends View implements ViewTreeObserver.OnGlobalLayoutListener, View.OnSystemUiVisibilityChangeListener {

    /**
     * WindowManager.LayoutParams
     */
    private final WindowManager.LayoutParams mParams;

    /**
     * ScreenListener
     */
    private final ScreenChangedListener mScreenChangedListener;

    /**
     * 最後の表示状態（onSystemUiVisibilityChangeが来ない場合があるので自分で保持）
     * ※来ない場合：ImmersiveMode→ステータスバーを触る→ステータスバーが消える
     */
    private int mLastUiVisibility;

    /**
     * WindowのRect
     */
    private final Rect mWindowRect;


    /**
     * コンストラクタ
     */
    FullscreenObserverView(Context context, ScreenChangedListener listener) {
        super(context);

        // リスナーのセット
        mScreenChangedListener = listener;

        // 幅1,高さ最大の透明なViewを用意して、レイアウトの変化を検知する
        mParams = new WindowManager.LayoutParams();
        mParams.width = 1;
        mParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        mParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        mParams.format = PixelFormat.TRANSLUCENT;

        mWindowRect = new Rect();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(this);
        setOnSystemUiVisibilityChangeListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDetachedFromWindow() {
        // レイアウトの変化通知を削除
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            getViewTreeObserver().removeOnGlobalLayoutListener(this);
        } else {
            //noinspection deprecation
            getViewTreeObserver().removeGlobalOnLayoutListener(this);
        }
        setOnSystemUiVisibilityChangeListener(null);
        super.onDetachedFromWindow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onGlobalLayout() {
        // View（フル画面）のサイズを取得
        if (mScreenChangedListener != null) {
            getWindowVisibleDisplayFrame(mWindowRect);
            mScreenChangedListener.onScreenChanged(mLastUiVisibility != View.SYSTEM_UI_FLAG_VISIBLE || mWindowRect.top == 0);
        }
    }

    /**
     * ナビゲーションバーに処理を行うアプリ（onGlobalLayoutのイベントが発生しない場合）で利用しています。
     * (Nexus5のカメラアプリなど)
     */
    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        mLastUiVisibility = visibility;
        // ナビゲーションバーの変化を受けて表示・非表示切替
        if (mScreenChangedListener != null) {
            getWindowVisibleDisplayFrame(mWindowRect);
            mScreenChangedListener.onScreenChanged(mLastUiVisibility != View.SYSTEM_UI_FLAG_VISIBLE || mWindowRect.top == 0);
        }
    }

    /**
     * WindowManager.LayoutParams
     *
     * @return WindowManager.LayoutParams
     */
    WindowManager.LayoutParams getWindowLayoutParams() {
        return mParams;
    }
}