/**
 * Copyright 2015 RECRUIT LIFESTYLE CO., LTD.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.recruit_lifestyle.android.floatingview;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.annotation.IntDef;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

/**
 * FloatingViewを扱うクラスです。
 * TODO:動作がカクカクなので原因を探す
 * TODO:移動を追従する複数表示サポートは第2弾で対応
 */
public class FloatingViewManager implements ScreenChangedListener, View.OnTouchListener, TrashViewListener {

    /**
     * 常に表示するモード
     */
    public static final int DISPLAY_MODE_SHOW_ALWAYS = 1;

    /**
     * 常に非表示にするモード
     */
    public static final int DISPLAY_MODE_HIDE_ALWAYS = 2;

    /**
     * フルスクリーン時に非表示にするモード
     */
    public static final int DISPLAY_MODE_HIDE_FULLSCREEN = 3;

    /**
     * 表示モード
     */
    @IntDef({DISPLAY_MODE_SHOW_ALWAYS, DISPLAY_MODE_HIDE_ALWAYS, DISPLAY_MODE_HIDE_FULLSCREEN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DisplayMode {
    }

    /**
     * 左右の近い方向に移動
     */
    public static final int MOVE_DIRECTION_DEFAULT = 0;
    /**
     * 常に左に移動
     */
    public static final int MOVE_DIRECTION_LEFT = 1;
    /**
     * 常に右に移動
     */
    public static final int MOVE_DIRECTION_RIGHT = 2;

    /**
     * 移動しない
     */
    public static final int MOVE_DIRECTION_NONE = 3;

    /**
     * 側に近づく方向に移動します
     */
    public static final int MOVE_DIRECTION_NEAREST = 4;


    /**
     * Moving direction
     */
    @IntDef({MOVE_DIRECTION_DEFAULT, MOVE_DIRECTION_LEFT, MOVE_DIRECTION_RIGHT, MOVE_DIRECTION_NEAREST, MOVE_DIRECTION_NONE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MoveDirection {
    }

    /**
     * Viewの形が円形の場合
     */
    public static final float SHAPE_CIRCLE = 1.0f;

    /**
     * Viewの形が四角形の場合
     */
    public static final float SHAPE_RECTANGLE = 1.4142f;

    /**
     * {@link Context}
     */
    private final Context mContext;

    /**
     * {@link Resources}
     */
    private final Resources mResources;

    /**
     * WindowManager
     */
    private final WindowManager mWindowManager;

    /**
     * {@link DisplayMetrics}
     */
    private final DisplayMetrics mDisplayMetrics;

    /**
     * 操作状態のFloatingView
     */
    private FloatingView mTargetFloatingView;

    /**
     * フルスクリーンを監視するViewです。
     */
    private final FullscreenObserverView mFullscreenObserverView;

    /**
     * FloatingViewを削除するViewです。
     */
    private final TrashView mTrashView;

    /**
     * FloatingViewListener
     */
    private final FloatingViewListener mFloatingViewListener;

    /**
     * FloatingViewの当たり判定用矩形
     */
    private final Rect mFloatingViewRect;

    /**
     * TrashViewの当たり判定用矩形
     */
    private final Rect mTrashViewRect;

    /**
     * タッチの移動を許可するフラグ
     * 画面回転時にタッチ処理を受け付けないようにするためのフラグです
     */
    private boolean mIsMoveAccept;

    /**
     * 現在の表示モード
     */
    @DisplayMode
    private int mDisplayMode;

    /**
     * Windowに貼り付けられたFloatingViewのリスト
     * TODO:第2弾のFloatingViewの複数表示で意味を発揮する予定
     */
    private final ArrayList<FloatingView> mFloatingViewList;

    /**
     * コンストラクタ
     *
     * @param context  Context
     * @param listener FloatingViewListener
     */
    public FloatingViewManager(Context context, FloatingViewListener listener) {
        mContext = context;
        mResources = context.getResources();
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mDisplayMetrics = new DisplayMetrics();
        mFloatingViewListener = listener;
        mFloatingViewRect = new Rect();
        mTrashViewRect = new Rect();
        mIsMoveAccept = false;
        mDisplayMode = DISPLAY_MODE_HIDE_FULLSCREEN;

        // FloatingViewと連携するViewの構築
        mFloatingViewList = new ArrayList<>();
        mFullscreenObserverView = new FullscreenObserverView(context, this);
        mTrashView = new TrashView(context);
    }

    /**
     * 削除Viewと重なっているかチェックします。
     *
     * @return 削除Viewと重なっている場合はtrue
     */
    private boolean isIntersectWithTrash() {
        // 無効の場合は重なり判定を行わない
        if (!mTrashView.isTrashEnabled()) {
            return false;
        }
        // INFO:TrashViewとFloatingViewは同じGravityにする必要があります
        mTrashView.getWindowDrawingRect(mTrashViewRect);
        mTargetFloatingView.getWindowDrawingRect(mFloatingViewRect);
        return Rect.intersects(mTrashViewRect, mFloatingViewRect);
    }

    /**
     * 画面がフルスクリーンになった場合はViewを非表示にします。
     */
    @Override
    public void onScreenChanged(Rect windowRect, int visibility) {
        // detect status bar
        final boolean isFitSystemWindowTop = windowRect.top == 0;
        boolean isHideStatusBar;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1 && visibility != FullscreenObserverView.NO_LAST_VISIBILITY) {
            // Support for screen rotation when setSystemUiVisibility is used
            isHideStatusBar = isFitSystemWindowTop || (visibility & View.SYSTEM_UI_FLAG_LOW_PROFILE) == View.SYSTEM_UI_FLAG_LOW_PROFILE;
        } else {
            isHideStatusBar = isFitSystemWindowTop;
        }

        // detect navigation bar
        final boolean isHideNavigationBar;
        if (visibility == FullscreenObserverView.NO_LAST_VISIBILITY) {
            // At the first it can not get the correct value, so do special processing
            mWindowManager.getDefaultDisplay().getMetrics(mDisplayMetrics);
            isHideNavigationBar = windowRect.width() - mDisplayMetrics.widthPixels > 0 || windowRect.height() - mDisplayMetrics.heightPixels > 0;
        } else {
            isHideNavigationBar = (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }
        final boolean isPortrait = mResources.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        // touch X offset(navigation bar is on the left side)
        final boolean hasTouchXOffset = windowRect.left > 0;

        // update FloatingView layout
        mTargetFloatingView.onUpdateSystemLayout(isHideStatusBar, isHideNavigationBar, isPortrait, hasTouchXOffset);

        // フルスクリーンでの非表示モードでない場合は何もしない
        if (mDisplayMode != DISPLAY_MODE_HIDE_FULLSCREEN) {
            return;
        }

        mIsMoveAccept = false;
        final int state = mTargetFloatingView.getState();
        // 重なっていない場合は全て非表示処理
        if (state == FloatingView.STATE_NORMAL) {
            final int size = mFloatingViewList.size();
            for (int i = 0; i < size; i++) {
                final FloatingView floatingView = mFloatingViewList.get(i);
                floatingView.setVisibility(isFitSystemWindowTop ? View.GONE : View.VISIBLE);
            }
            mTrashView.dismiss();
        }
        // 重なっている場合は削除
        else if (state == FloatingView.STATE_INTERSECTING) {
            mTargetFloatingView.setFinishing();
            mTrashView.dismiss();
        }
    }

    /**
     * Update ActionTrashIcon
     */
    @Override
    public void onUpdateActionTrashIcon() {
        mTrashView.updateActionTrashIcon(mTargetFloatingView.getMeasuredWidth(), mTargetFloatingView.getMeasuredHeight(), mTargetFloatingView.getShape());
    }

    /**
     * FloatingViewのタッチをロックします。
     */
    @Override
    public void onTrashAnimationStarted(@TrashView.AnimationState int animationCode) {
        // クローズまたは強制クローズの場合はすべてのFloatingViewをタッチさせない
        if (animationCode == TrashView.ANIMATION_CLOSE || animationCode == TrashView.ANIMATION_FORCE_CLOSE) {
            final int size = mFloatingViewList.size();
            for (int i = 0; i < size; i++) {
                final FloatingView floatingView = mFloatingViewList.get(i);
                floatingView.setDraggable(false);
            }
        }
    }

    /**
     * FloatingViewのタッチロックの解除を行います。
     */
    @Override
    public void onTrashAnimationEnd(@TrashView.AnimationState int animationCode) {

        final int state = mTargetFloatingView.getState();
        // 終了していたらViewを削除する
        if (state == FloatingView.STATE_FINISHING) {
            removeViewToWindow(mTargetFloatingView);
        }

        // すべてのFloatingViewのタッチ状態を戻す
        final int size = mFloatingViewList.size();
        for (int i = 0; i < size; i++) {
            final FloatingView floatingView = mFloatingViewList.get(i);
            floatingView.setDraggable(true);
        }

    }

    /**
     * 削除ボタンの表示・非表示を処理します。
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final int action = event.getAction();

        // 押下状態でないのに移動許可が出ていない場合はなにもしない(回転直後にACTION_MOVEが来て、FloatingViewが消えてしまう現象に対応)
        if (action != MotionEvent.ACTION_DOWN && !mIsMoveAccept) {
            return false;
        }

        final int state = mTargetFloatingView.getState();
        mTargetFloatingView = (FloatingView) v;

        // 押下
        if (action == MotionEvent.ACTION_DOWN) {
            // 処理なし
            mIsMoveAccept = true;
        }
        // 移動
        else if (action == MotionEvent.ACTION_MOVE) {
            // 今回の状態
            final boolean isIntersecting = isIntersectWithTrash();
            // これまでの状態
            final boolean isIntersect = state == FloatingView.STATE_INTERSECTING;
            // 重なっている場合は、FloatingViewをTrashViewに追従させる
            if (isIntersecting) {
                mTargetFloatingView.setIntersecting((int) mTrashView.getTrashIconCenterX(), (int) mTrashView.getTrashIconCenterY());
            }
            // 重なり始めの場合
            if (isIntersecting && !isIntersect) {
                mTargetFloatingView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                mTrashView.setScaleTrashIcon(true);
            }
            // 重なり終わりの場合
            else if (!isIntersecting && isIntersect) {
                mTargetFloatingView.setNormal();
                mTrashView.setScaleTrashIcon(false);
            }

        }
        // 押上、キャンセル
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // 重なっている場合
            if (state == FloatingView.STATE_INTERSECTING) {
                // FloatingViewを削除し、拡大状態を解除
                mTargetFloatingView.setFinishing();
                mTrashView.setScaleTrashIcon(false);
            }
            mIsMoveAccept = false;

            // Touch finish callback
            if (mFloatingViewListener != null) {
                final boolean isFinishing = mTargetFloatingView.getState() == FloatingView.STATE_FINISHING;
                final WindowManager.LayoutParams params = mTargetFloatingView.getWindowLayoutParams();
                mFloatingViewListener.onTouchFinished(mTargetFloatingView, isFinishing, params.x, params.y);
            }
        }

        // TrashViewにイベントを通知
        // 通常状態の場合は指の位置を渡す
        // 重なっている場合はTrashViewの位置を渡す
        if (state == FloatingView.STATE_INTERSECTING) {
            mTrashView.onTouchFloatingView(event, mFloatingViewRect.left, mFloatingViewRect.top);
        } else {
            final WindowManager.LayoutParams params = mTargetFloatingView.getWindowLayoutParams();
            mTrashView.onTouchFloatingView(event, params.x, params.y);
        }

        return false;
    }

    /**
     * 固定削除アイコンの画像を設定します。
     *
     * @param resId drawable ID
     */
    public void setFixedTrashIconImage(@DrawableRes int resId) {
        mTrashView.setFixedTrashIconImage(resId);
    }

    /**
     * アクションする削除アイコンの画像を設定します。
     *
     * @param resId drawable ID
     */
    public void setActionTrashIconImage(@DrawableRes int resId) {
        mTrashView.setActionTrashIconImage(resId);
    }

    /**
     * 固定削除アイコンを設定します。
     *
     * @param drawable Drawable
     */
    public void setFixedTrashIconImage(Drawable drawable) {
        mTrashView.setFixedTrashIconImage(drawable);
    }

    /**
     * アクション用削除アイコンを設定します。
     *
     * @param drawable Drawable
     */
    public void setActionTrashIconImage(Drawable drawable) {
        mTrashView.setActionTrashIconImage(drawable);
    }

    /**
     * 表示モードを変更します。
     *
     * @param displayMode {@link #DISPLAY_MODE_SHOW_ALWAYS} or {@link #DISPLAY_MODE_HIDE_ALWAYS} or {@link #DISPLAY_MODE_HIDE_FULLSCREEN}
     */
    public void setDisplayMode(@DisplayMode int displayMode) {
        mDisplayMode = displayMode;
        // 常に表示/フルスクリーン時に非表示にするモードの場合
        if (mDisplayMode == DISPLAY_MODE_SHOW_ALWAYS || mDisplayMode == DISPLAY_MODE_HIDE_FULLSCREEN) {
            for (FloatingView floatingView : mFloatingViewList) {
                floatingView.setVisibility(View.VISIBLE);
            }
        }
        // 常に非表示にするモードの場合
        else if (mDisplayMode == DISPLAY_MODE_HIDE_ALWAYS) {
            for (FloatingView floatingView : mFloatingViewList) {
                floatingView.setVisibility(View.GONE);
            }
            mTrashView.dismiss();
        }
    }

    /**
     * TrashViewの表示・非表示を設定します。
     *
     * @param enabled trueの場合は表示
     */
    public void setTrashViewEnabled(boolean enabled) {
        mTrashView.setTrashEnabled(enabled);
    }

    /**
     * TrashViewの表示非表示状態を取得します。
     *
     * @return trueの場合は表示状態（重なり判定が有効の状態）
     */
    public boolean isTrashViewEnabled() {
        return mTrashView.isTrashEnabled();
    }

    /**
     * ViewをWindowに貼り付けます。
     *
     * @param view    フローティングさせるView
     * @param options Options
     * @return floatingView
     */
    public FloatingView addViewToWindow(View view, Options options) {
        final boolean isFirstAttach = mFloatingViewList.isEmpty();
        // FloatingView
        final FloatingView floatingView = new FloatingView(mContext);
        floatingView.setInitCoords(options.floatingViewX, options.floatingViewY);
        floatingView.setOnTouchListener(this);
        floatingView.setShape(options.shape);
        floatingView.setOverMargin(options.overMargin);
        floatingView.setMoveDirection(options.moveDirection);
        floatingView.setAnimateInitialMove(options.animateInitialMove);

        // set FloatingView size
        final FrameLayout.LayoutParams targetParams = new FrameLayout.LayoutParams(options.floatingViewWidth, options.floatingViewHeight);
        view.setLayoutParams(targetParams);
        floatingView.setTag(view.getTag());
        floatingView.addView(view);

        // 非表示モードの場合
        if (mDisplayMode == DISPLAY_MODE_HIDE_ALWAYS) {
            floatingView.setVisibility(View.GONE);
        }
        mFloatingViewList.add(floatingView);
        // TrashView
        mTrashView.setTrashViewListener(this);

        // Viewの貼り付け
        mWindowManager.addView(floatingView, floatingView.getWindowLayoutParams());
        // 最初の貼り付け時の場合のみ、フルスクリーン監視Viewと削除Viewを貼り付け
        if (isFirstAttach) {
            mWindowManager.addView(mFullscreenObserverView, mFullscreenObserverView.getWindowLayoutParams());
            mTargetFloatingView = floatingView;
        } else {
            mWindowManager.removeViewImmediate(mTrashView);
        }
        // 必ずトップに来て欲しいので毎回貼り付け
        mWindowManager.addView(mTrashView, mTrashView.getWindowLayoutParams());

        return floatingView;
    }

    /**
     * ViewをWindowから取り外します。
     *
     * @param floatingView FloatingView
     */
    public void removeViewToWindow(FloatingView floatingView) {
        final int matchIndex = mFloatingViewList.indexOf(floatingView);
        // 見つかった場合は表示とリストから削除
        if (matchIndex != -1) {
            mWindowManager.removeViewImmediate(floatingView);
            mFloatingViewList.remove(matchIndex);
        }

        // 残りのViewをチェック
        if (mFloatingViewList.isEmpty()) {
            // 終了を通知
            if (mFloatingViewListener != null) {
                mFloatingViewListener.onFinishFloatingView();
            }
        }
    }

    /**
     * ViewをWindowから全て取り外します。
     */
    public void removeAllViewToWindow() {
        mWindowManager.removeViewImmediate(mFullscreenObserverView);
        mWindowManager.removeViewImmediate(mTrashView);
        // FloatingViewの削除
        final int size = mFloatingViewList.size();
        for (int i = 0; i < size; i++) {
            final FloatingView floatingView = mFloatingViewList.get(i);
            mWindowManager.removeViewImmediate(floatingView);
        }
        mFloatingViewList.clear();
    }

    public void setOnKeyListener(View.OnKeyListener listener){
        mFullscreenObserverView.setOnKeyListener(listener);
    }

    /**
     * FloatingViewを貼り付ける際のオプションを表すクラスです。
     */
    public static class Options {

        /**
         * フローティングさせるViewの矩形（SHAPE_RECTANGLE or SHAPE_CIRCLE）
         */
        public float shape;

        /**
         * 画面外のはみ出しマージン(px)
         */
        public int overMargin;

        /**
         * 画面左下を原点とするFloatingViewのX座標
         */
        public int floatingViewX;

        /**
         * 画面左下を原点とするFloatingViewのY座標
         */
        public int floatingViewY;

        /**
         * Width of FloatingView(px)
         */
        public int floatingViewWidth;

        /**
         * Height of FloatingView(px)
         */
        public int floatingViewHeight;

        /**
         * FloatingViewが吸着する方向
         * ※座標を指定すると自動的にMOVE_DIRECTION_NONEになります
         */
        @MoveDirection
        public int moveDirection;

        /**
         * 初期表示時にアニメーションするフラグ
         */
        public boolean animateInitialMove;

        /**
         * オプションのデフォルト値を設定します。
         */
        public Options() {
            shape = SHAPE_CIRCLE;
            overMargin = 0;
            floatingViewX = FloatingView.DEFAULT_X;
            floatingViewY = FloatingView.DEFAULT_Y;
            floatingViewWidth = FloatingView.DEFAULT_WIDTH;
            floatingViewHeight = FloatingView.DEFAULT_HEIGHT;
            moveDirection = MOVE_DIRECTION_DEFAULT;
            animateInitialMove = true;
        }

    }

}
