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

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

/**
 * FloatingViewを消すためのViewです。
 */
class TrashView extends FrameLayout implements ViewTreeObserver.OnPreDrawListener {

    /**
     * 背景の高さ(dp)
     */
    private static final int BACKGROUND_HEIGHT = 164;

    /**
     * ターゲットを取り込む水平領域(dp)
     */
    private static final float TARGET_CAPTURE_HORIZONTAL_REGION = 30.0f;

    /**
     * ターゲットを取り込む垂直領域(dp)
     */
    private static final float TARGET_CAPTURE_VERTICAL_REGION = 4.0f;

    /**
     * 削除アイコンの拡大・縮小のアニメーション時間
     */
    private static final long TRASH_ICON_SCALE_DURATION_MILLIS = 200L;

    /**
     * アニメーションなしの状態を表す定数
     */
    static final int ANIMATION_NONE = 0;
    /**
     * 背景・削除アイコンなどを表示するアニメーションを表す定数<br/>
     * FloatingViewの追尾も含みます。
     */
    static final int ANIMATION_OPEN = 1;
    /**
     * 背景・削除アイコンなどを消すアニメーションを表す定数
     */
    static final int ANIMATION_CLOSE = 2;
    /**
     * 背景・削除アイコンなどを即時に消すことを表す定数
     */
    static final int ANIMATION_FORCE_CLOSE = 3;

    /**
     * 長押し判定とする時間
     */
    private static final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

    /**
     * WindowManager
     */
    private final WindowManager mWindowManager;

    /**
     * LayoutParams
     */
    private final WindowManager.LayoutParams mParams;

    /**
     * DisplayMetrics
     */
    private final DisplayMetrics mMetrics;

    /**
     * ルートView（背景、削除アイコンを含むView）
     */
    private final ViewGroup mRootView;

    /**
     * 削除アイコン
     */
    private final FrameLayout mTrashIconRootView;

    /**
     * 固定された削除アイコン
     */
    private final ImageView mFixedTrashIconView;

    /**
     * 重なりに応じて動作する削除アイコン
     */
    private final ImageView mActionTrashIconView;

    /**
     * ActionTrashIconの幅
     */
    private int mActionTrashIconBaseWidth;

    /**
     * ActionTrashIconの高さ
     */
    private int mActionTrashIconBaseHeight;

    /**
     * ActionTrashIconの最大拡大率
     */
    private float mActionTrashIconMaxScale;

    /**
     * 背景View
     */
    private final FrameLayout mBackgroundView;

    /**
     * 削除アイコンの枠内に入った時のアニメーション（拡大）
     */
    private ObjectAnimator mEnterScaleAnimator;

    /**
     * 削除アイコンの枠外に出た時のアニメーション（縮小）
     */
    private ObjectAnimator mExitScaleAnimator;

    /**
     * アニメーションを行うハンドラ
     */
    private final AnimationHandler mAnimationHandler;

    /**
     * TrashViewListener
     */
    private TrashViewListener mTrashViewListener;

    /**
     * Viewの有効・無効フラグ（無効の場合は表示されない）
     */
    private boolean mIsEnabled;

    /**
     * コンストラクタ
     *
     * @param context Context
     */
    TrashView(Context context) {
        super(context);
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mMetrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(mMetrics);
        mAnimationHandler = new AnimationHandler(this);
        mIsEnabled = true;

        mParams = new WindowManager.LayoutParams();
        mParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        mParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mParams.type = WindowManager.LayoutParams.TYPE_PRIORITY_PHONE;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        mParams.format = PixelFormat.TRANSLUCENT;
        // INFO:Windowの原点のみ左下に設定
        mParams.gravity = Gravity.LEFT | Gravity.BOTTOM;

        // 各種Viewの設定
        // TrashViewに直接貼り付けられるView（このViewを介さないと、削除Viewと背景Viewのレイアウトがなぜか崩れる）
        mRootView = new FrameLayout(context);
        // 削除アイコンのルートView
        mTrashIconRootView = new FrameLayout(context);
        mFixedTrashIconView = new ImageView(context);
        mActionTrashIconView = new ImageView(context);
        // 背景View
        mBackgroundView = new FrameLayout(context);
        mBackgroundView.setAlpha(0.0f);
        final GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x50000000});
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            //noinspection deprecation
            mBackgroundView.setBackgroundDrawable(gradientDrawable);
        } else {
            mBackgroundView.setBackground(gradientDrawable);
        }

        // 背景Viewの貼り付け
        final FrameLayout.LayoutParams backgroundParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (BACKGROUND_HEIGHT * mMetrics.density));
        mRootView.addView(mBackgroundView, backgroundParams);
        // アクションアイコンの貼り付け
        final FrameLayout.LayoutParams actionTrashIconParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionTrashIconParams.gravity = Gravity.CENTER;
        mTrashIconRootView.addView(mActionTrashIconView, actionTrashIconParams);
        // 固定アイコンの貼付け
        final FrameLayout.LayoutParams fixedTrashIconParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fixedTrashIconParams.gravity = Gravity.CENTER;
        mTrashIconRootView.addView(mFixedTrashIconView, fixedTrashIconParams);
        // 削除アイコンの貼り付け
        final FrameLayout.LayoutParams trashIconParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trashIconParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        mRootView.addView(mTrashIconRootView, trashIconParams);

        // TrashViewに貼り付け
        addView(mRootView);

        // 初回描画処理用
        getViewTreeObserver().addOnPreDrawListener(this);
    }

    /**
     * 表示位置を決定します。
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateViewLayout();
    }

    /**
     * 画面回転時にレイアウトの調整をします。
     */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateViewLayout();
    }

    /**
     * 初回描画時の座標設定を行います。<br/>
     * 初回表示時に一瞬だけ削除アイコンが表示される事象があるため。
     */
    @Override
    public boolean onPreDraw() {
        getViewTreeObserver().removeOnPreDrawListener(this);
        mTrashIconRootView.setTranslationY(mTrashIconRootView.getMeasuredHeight());
        return true;
    }

    /**
     * 画面サイズから自位置を決定します。
     */
    private void updateViewLayout() {
        mWindowManager.getDefaultDisplay().getMetrics(mMetrics);
        mParams.x = (mMetrics.widthPixels - getWidth()) / 2;
        mParams.y = 0;

        // アニメーション側情報を更新
        mAnimationHandler.onUpdateViewLayout();

        mWindowManager.updateViewLayout(this, mParams);
    }

    /**
     * TrashViewを非表示にします。
     */
    void dismiss() {
        // アニメーション停止
        mAnimationHandler.removeMessages(ANIMATION_OPEN);
        mAnimationHandler.removeMessages(ANIMATION_CLOSE);
        mAnimationHandler.sendAnimationMessage(ANIMATION_FORCE_CLOSE);
        // 拡大アニメーションの停止
        setScaleTrashIconImmediately(false);
    }

    /**
     * Window上での描画領域を取得します。
     * 当たり判定の矩形を表します。
     *
     * @param outRect 変更を加えるRect
     */
    void getWindowDrawingRect(Rect outRect) {
        // Gravityが逆向きなので、矩形の当たり判定も上下逆転(top/bottom)
        // top(画面上で下方向)の判定を多めに設定
        final ImageView iconView = hasActionTrashIcon() ? mActionTrashIconView : mFixedTrashIconView;
        final float iconPaddingLeft = iconView.getPaddingLeft();
        final float iconPaddingTop = iconView.getPaddingTop();
        final float iconWidth = iconView.getWidth() - iconPaddingLeft - iconView.getPaddingRight();
        final float iconHeight = iconView.getHeight() - iconPaddingTop - iconView.getPaddingBottom();
        final float x = mTrashIconRootView.getX() + iconPaddingLeft;
        final float y = mRootView.getHeight() - mTrashIconRootView.getY() - iconPaddingTop - iconHeight;
        final int left = (int) (x - TARGET_CAPTURE_HORIZONTAL_REGION * mMetrics.density);
        final int top = -mRootView.getHeight();
        final int right = (int) (x + iconWidth + TARGET_CAPTURE_HORIZONTAL_REGION * mMetrics.density);
        final int bottom = (int) (y + iconHeight + TARGET_CAPTURE_VERTICAL_REGION * mMetrics.density);
        outRect.set(left, top, right, bottom);
    }

    /**
     * アクションする削除アイコンのパディングを設定します。
     *
     * @param width  対象となるViewの幅
     * @param height 対象となるViewの高さ
     * @param shape  対象となるViewの形状
     */
    void calcActionTrashIconPadding(float width, float height, float shape) {
        // アクションする削除アイコンが設定されていない場合は何もしない
        if (!hasActionTrashIcon()) {
            return;
        }
        // 拡大率の設定
        mAnimationHandler.mTargetWidth = width;
        mAnimationHandler.mTargetHeight = height;
        final float newWidthScale = width / mActionTrashIconBaseWidth * shape;
        final float newHeightScale = height / mActionTrashIconBaseHeight * shape;
        mActionTrashIconMaxScale = Math.max(newWidthScale, newHeightScale);
        // ENTERアニメーション作成
        mEnterScaleAnimator = ObjectAnimator.ofPropertyValuesHolder(mActionTrashIconView, PropertyValuesHolder.ofFloat(ImageView.SCALE_X, mActionTrashIconMaxScale), PropertyValuesHolder.ofFloat(ImageView.SCALE_Y, mActionTrashIconMaxScale));
        mEnterScaleAnimator.setInterpolator(new OvershootInterpolator());
        mEnterScaleAnimator.setDuration(TRASH_ICON_SCALE_DURATION_MILLIS);
        // Exitアニメーション作成
        mExitScaleAnimator = ObjectAnimator.ofPropertyValuesHolder(mActionTrashIconView, PropertyValuesHolder.ofFloat(ImageView.SCALE_X, 1.0f), PropertyValuesHolder.ofFloat(ImageView.SCALE_Y, 1.0f));
        mExitScaleAnimator.setInterpolator(new OvershootInterpolator());
        mExitScaleAnimator.setDuration(TRASH_ICON_SCALE_DURATION_MILLIS);

        // 重なった際の拡大時にフィットするようにパディングの設定
        final int horizontalPadding = Math.max((int) ((mActionTrashIconMaxScale - 1.0f) * mActionTrashIconBaseWidth / 2 + 0.5f), 0);
        final int verticalPadding = Math.max((int) ((mActionTrashIconMaxScale - 1.0f) * mActionTrashIconBaseHeight / 2 + 0.5f), 0);
        mActionTrashIconView.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
    }

    /**
     * 削除アイコンの中心X座標を取得します。
     *
     * @return 削除アイコンの中心X座標
     */
    float getTrashIconCenterX() {
        final ImageView iconView = hasActionTrashIcon() ? mActionTrashIconView : mFixedTrashIconView;
        final float iconViewPaddingLeft = iconView.getPaddingLeft();
        final float iconWidth = iconView.getWidth() - iconViewPaddingLeft - iconView.getPaddingRight();
        final float x = mTrashIconRootView.getX() + iconViewPaddingLeft;
        return x + iconWidth / 2;
    }

    /**
     * 削除アイコンの中心Y座標を取得します。
     *
     * @return 削除アイコンの中心Y座標
     */
    float getTrashIconCenterY() {
        final ImageView iconView = hasActionTrashIcon() ? mActionTrashIconView : mFixedTrashIconView;
        final float iconViewHeight = iconView.getHeight();
        final float iconViewPaddingBottom = iconView.getPaddingBottom();
        final float iconHeight = iconViewHeight - iconView.getPaddingTop() - iconViewPaddingBottom;
        final float y = mRootView.getHeight() - mTrashIconRootView.getY() - iconViewHeight + iconViewPaddingBottom;
        return y + iconHeight / 2;
    }


    /**
     * アクションする削除アイコンが存在するかチェックします。
     *
     * @return アクションする削除アイコンが存在する場合はtrue
     */
    private boolean hasActionTrashIcon() {
        return mActionTrashIconBaseWidth != 0 && mActionTrashIconBaseHeight != 0;
    }

    /**
     * 固定削除アイコンの画像を設定します。<br/>
     * この画像はフローティング表示が重なった際に大きさが変化しません。
     *
     * @param resId drawable ID
     */
    void setFixedTrashIconImage(int resId) {
        mFixedTrashIconView.setImageResource(resId);
    }

    /**
     * アクションする削除アイコンの画像を設定します。<br/>
     * この画像はフローティング表示が重なった際に大きさが変化します。
     *
     * @param resId drawable ID
     */
    void setActionTrashIconImage(int resId) {
        mActionTrashIconView.setImageResource(resId);
        final Drawable drawable = mActionTrashIconView.getDrawable();
        if (drawable != null) {
            mActionTrashIconBaseWidth = drawable.getIntrinsicWidth();
            mActionTrashIconBaseHeight = drawable.getIntrinsicHeight();
        }
    }

    /**
     * 固定削除アイコンを設定します。<br/>
     * この画像はフローティング表示が重なった際に大きさが変化しません。
     *
     * @param drawable Drawable
     */
    void setFixedTrashIconImage(Drawable drawable) {
        mFixedTrashIconView.setImageDrawable(drawable);
    }

    /**
     * アクション用削除アイコンを設定します。<br/>
     * この画像はフローティング表示が重なった際に大きさが変化します。
     *
     * @param drawable Drawable
     */
    void setActionTrashIconImage(Drawable drawable) {
        mActionTrashIconView.setImageDrawable(drawable);
        if (drawable != null) {
            mActionTrashIconBaseWidth = drawable.getIntrinsicWidth();
            mActionTrashIconBaseHeight = drawable.getIntrinsicHeight();
        }
    }

    /**
     * 削除アイコンの大きさを即時に変更します。
     *
     * @param isEnter 領域に入った場合はtrue.そうでない場合はfalse
     */
    private void setScaleTrashIconImmediately(boolean isEnter) {
        cancelScaleTrashAnimation();

        mActionTrashIconView.setScaleX(isEnter ? mActionTrashIconMaxScale : 1.0f);
        mActionTrashIconView.setScaleY(isEnter ? mActionTrashIconMaxScale : 1.0f);
    }

    /**
     * 削除アイコンの大きさを変更します。
     *
     * @param isEnter 領域に入った場合はtrue.そうでない場合はfalse
     */
    void setScaleTrashIcon(boolean isEnter) {
        // アクションアイコンが設定されていなければ何もしない
        if (!hasActionTrashIcon()) {
            return;
        }

        // アニメーションをキャンセル
        cancelScaleTrashAnimation();

        // 領域に入った場合
        if (isEnter) {
            mEnterScaleAnimator.start();
        } else {
            mExitScaleAnimator.start();
        }
    }

    /**
     * TrashViewの有効・無効を設定します。
     *
     * @param enabled trueの場合は有効（表示）、falseの場合は無効（非表示）
     */
    void setTrashEnabled(boolean enabled) {
        // 設定が同じ場合は何もしない
        if (mIsEnabled == enabled) {
            return;
        }

        // 非表示にする場合は閉じる
        mIsEnabled = enabled;
        if (!mIsEnabled) {
            dismiss();
        }
    }

    /**
     * TrashViewの表示状態を取得します。
     *
     * @return trueの場合は表示
     */
    boolean isTrashEnabled() {
        return mIsEnabled;
    }

    /**
     * 削除アイコンの拡大・縮小アニメーションのキャンセル
     */
    private void cancelScaleTrashAnimation() {
        // 枠内アニメーション
        if (mEnterScaleAnimator != null && mEnterScaleAnimator.isStarted()) {
            mEnterScaleAnimator.cancel();
        }

        // 枠外アニメーション
        if (mExitScaleAnimator != null && mExitScaleAnimator.isStarted()) {
            mExitScaleAnimator.cancel();
        }
    }

    /**
     * TrashViewListenerを設定します。
     *
     * @param listener TrashViewListener
     */
    void setTrashViewListener(TrashViewListener listener) {
        mTrashViewListener = listener;
    }

    /**
     * WindowManager.LayoutParams
     *
     * @return WindowManager.LayoutParams
     */
    WindowManager.LayoutParams getWindowLayoutParams() {
        return mParams;
    }

    /**
     * FloatingViewに関連する処理を行います。
     *
     * @param event MotionEvent
     * @param x     FloatingViewのX座標
     * @param y     FloatingViewのY座標
     */
    void onTouchFloatingView(MotionEvent event, float x, float y) {
        final int action = event.getAction();
        // 押下
        if (action == MotionEvent.ACTION_DOWN) {
            mAnimationHandler.updateTargetPosition(x, y);
            // 長押し処理待ち
            mAnimationHandler.removeMessages(ANIMATION_CLOSE);
            mAnimationHandler.sendAnimationMessageDelayed(ANIMATION_OPEN, LONG_PRESS_TIMEOUT);
        }
        // 移動
        else if (action == MotionEvent.ACTION_MOVE) {
            mAnimationHandler.updateTargetPosition(x, y);
            // まだオープンアニメーションが開始していない場合のみ実行
            if (!mAnimationHandler.isAnimationStarted(ANIMATION_OPEN)) {
                // 長押しのメッセージを削除
                mAnimationHandler.removeMessages(ANIMATION_OPEN);
                // オープン
                mAnimationHandler.sendAnimationMessage(ANIMATION_OPEN);
            }
        }
        // 押上、キャンセル
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // 長押しのメッセージを削除
            mAnimationHandler.removeMessages(ANIMATION_OPEN);
            mAnimationHandler.sendAnimationMessage(ANIMATION_CLOSE);
        }
    }

    /**
     * アニメーションの制御を行うハンドラです。
     */
    static class AnimationHandler extends Handler {

        /**
         * アニメーションをリフレッシュするミリ秒
         */
        private static final long ANIMATION_REFRESH_TIME_MILLIS = 17L;

        /**
         * 背景のアニメーション時間
         */
        private static final long BACKGROUND_DURATION_MILLIS = 200L;

        /**
         * 削除アイコンのポップアニメーションの開始遅延時間
         */
        private static final long TRASH_OPEN_START_DELAY_MILLIS = 200L;

        /**
         * 削除アイコンのオープンアニメーション時間
         */
        private static final long TRASH_OPEN_DURATION_MILLIS = 400L;

        /**
         * 削除アイコンのクローズアニメーション時間
         */
        private static final long TRASH_CLOSE_DURATION_MILLIS = 200L;

        /**
         * Overshootアニメーションの係数
         */
        private static final float OVERSHOOT_TENSION = 1.0f;

        /**
         * 削除アイコンの移動限界X軸オフセット(dp)
         */
        private static final int TRASH_MOVE_LIMIT_OFFSET_X = 22;

        /**
         * 削除アイコンの移動限界Y軸オフセット(dp)
         */
        private static final int TRASH_MOVE_LIMIT_TOP_OFFSET = -4;

        /**
         * アニメーション開始を表す定数
         */
        private static final int TYPE_FIRST = 1;
        /**
         * アニメーション更新を表す定数
         */
        private static final int TYPE_UPDATE = 2;

        /**
         * アルファの最大値
         */
        private static final float MAX_ALPHA = 1.0f;

        /**
         * アルファの最小値
         */
        private static final float MIN_ALPHA = 0.0f;

        /**
         * アニメーションを開始した時間
         */
        private long mStartTime;

        /**
         * アニメーションを始めた時点のアルファ値
         */
        private float mStartAlpha;

        /**
         * アニメーションを始めた時点のTransitionY
         */
        private float mStartTransitionY;

        /**
         * 実行中のアニメーションのコード
         */
        private int mStartedCode;

        /**
         * 追従対象のX座標
         */
        private float mTargetPositionX;

        /**
         * 追従対象のY座標
         */
        private float mTargetPositionY;

        /**
         * 追従対象の幅
         */
        private float mTargetWidth;

        /**
         * 追従対象の高さ
         */
        private float mTargetHeight;

        /**
         * 削除アイコンの移動限界位置
         */
        private final Rect mTrashIconLimitPosition;

        /**
         * Y軸の追従の範囲
         */
        private float mMoveStickyYRange;

        /**
         * OvershootInterpolator
         */
        private final OvershootInterpolator mOvershootInterpolator;


        /**
         * TrashView
         */
        private final WeakReference<TrashView> mTrashView;

        /**
         * コンストラクタ
         */
        AnimationHandler(TrashView trashView) {
            mTrashView = new WeakReference<>(trashView);
            mStartedCode = ANIMATION_NONE;
            mTrashIconLimitPosition = new Rect();
            mOvershootInterpolator = new OvershootInterpolator(OVERSHOOT_TENSION);
        }

        /**
         * アニメーションの処理を行います。
         */
        @Override
        public void handleMessage(Message msg) {
            final TrashView trashView = mTrashView.get();
            if (trashView == null) {
                removeMessages(ANIMATION_OPEN);
                removeMessages(ANIMATION_CLOSE);
                removeMessages(ANIMATION_FORCE_CLOSE);
                return;
            }

            // 有効でない場合はアニメーションを行わない
            if (!trashView.isTrashEnabled()) {
                return;
            }

            final int animationCode = msg.what;
            final int animationType = msg.arg1;
            final FrameLayout backgroundView = trashView.mBackgroundView;
            final FrameLayout trashIconRootView = trashView.mTrashIconRootView;
            final TrashViewListener listener = trashView.mTrashViewListener;
            final float screenWidth = trashView.mMetrics.widthPixels;
            final float trashViewX = trashView.mParams.x;

            // アニメーションを開始した場合の初期化
            if (animationType == TYPE_FIRST) {
                mStartTime = SystemClock.uptimeMillis();
                mStartAlpha = backgroundView.getAlpha();
                mStartTransitionY = trashIconRootView.getTranslationY();
                mStartedCode = animationCode;
                if (listener != null) {
                    listener.onTrashAnimationStarted(mStartedCode);
                }
            }
            // 経過時間
            final float elapsedTime = SystemClock.uptimeMillis() - mStartTime;

            // 表示アニメーション
            if (animationCode == ANIMATION_OPEN) {
                final float currentAlpha = backgroundView.getAlpha();
                // 最大のアルファ値に達していない場合
                if (currentAlpha < MAX_ALPHA) {
                    final float alphaTimeRate = Math.min(elapsedTime / BACKGROUND_DURATION_MILLIS, 1.0f);
                    final float alpha = Math.min(mStartAlpha + alphaTimeRate, MAX_ALPHA);
                    backgroundView.setAlpha(alpha);
                }

                // DelayTimeを超えていたらアニメーション開始
                if (elapsedTime >= TRASH_OPEN_START_DELAY_MILLIS) {
                    final float screenHeight = trashView.mMetrics.heightPixels;
                    // アイコンが左右に全部はみ出たらそれぞれ0%、100%の計算
                    final float positionX = trashViewX + (mTargetPositionX + mTargetWidth) / (screenWidth + mTargetWidth) * mTrashIconLimitPosition.width() + mTrashIconLimitPosition.left;
                    // 削除アイコンのY座標アニメーションと追従（上方向がマイナス）
                    // targetPositionYRateは、ターゲットのY座標が完全に画面外になると0%、画面の半分以降は100%
                    // stickyPositionYは移動限界の下端が原点で上端まで移動する。mMoveStickyRangeが追従の範囲
                    // positionYの計算により時間経過とともに移動する
                    final float targetPositionYRate = Math.min(2 * (mTargetPositionY + mTargetHeight) / (screenHeight + mTargetHeight), 1.0f);
                    final float stickyPositionY = mMoveStickyYRange * targetPositionYRate + mTrashIconLimitPosition.height() - mMoveStickyYRange;
                    final float translationYTimeRate = Math.min((elapsedTime - TRASH_OPEN_START_DELAY_MILLIS) / TRASH_OPEN_DURATION_MILLIS, 1.0f);
                    final float positionY = mTrashIconLimitPosition.bottom - stickyPositionY * mOvershootInterpolator.getInterpolation(translationYTimeRate);
                    trashIconRootView.setTranslationX(positionX);
                    trashIconRootView.setTranslationY(positionY);
                }

                sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS);
            }
            // 非表示アニメーション
            else if (animationCode == ANIMATION_CLOSE) {
                // アルファ値の計算
                final float alphaElapseTimeRate = Math.min(elapsedTime / BACKGROUND_DURATION_MILLIS, 1.0f);
                final float alpha = Math.max(mStartAlpha - alphaElapseTimeRate, MIN_ALPHA);
                backgroundView.setAlpha(alpha);

                // 削除アイコンのY座標アニメーション
                final float translationYTimeRate = Math.min(elapsedTime / TRASH_CLOSE_DURATION_MILLIS, 1.0f);
                // アニメーションが最後まで到達していない場合
                if (alphaElapseTimeRate < 1.0f || translationYTimeRate < 1.0f) {
                    final float position = mStartTransitionY + mTrashIconLimitPosition.height() * translationYTimeRate;
                    trashIconRootView.setTranslationY(position);
                    sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS);
                } else {
                    // 位置を強制的に調整
                    trashIconRootView.setTranslationY(mTrashIconLimitPosition.bottom);
                    mStartedCode = ANIMATION_NONE;
                    if (listener != null) {
                        listener.onTrashAnimationEnd(ANIMATION_CLOSE);
                    }
                }
            }
            // 即時非表示
            else if (animationCode == ANIMATION_FORCE_CLOSE) {
                backgroundView.setAlpha(0.0f);
                trashIconRootView.setTranslationY(mTrashIconLimitPosition.bottom);
                mStartedCode = ANIMATION_NONE;
                if (listener != null) {
                    listener.onTrashAnimationEnd(ANIMATION_FORCE_CLOSE);
                }
            }
        }

        /**
         * アニメーションのメッセージを送信します。
         *
         * @param animation   ANIMATION_OPEN,ANIMATION_CLOSE,ANIMATION_FORCE_CLOSE
         * @param delayMillis メッセージの送信時間
         */
        void sendAnimationMessageDelayed(int animation, long delayMillis) {
            sendMessageAtTime(newMessage(animation, TYPE_FIRST), SystemClock.uptimeMillis() + delayMillis);
        }

        /**
         * アニメーションのメッセージを送信します。
         *
         * @param animation ANIMATION_OPEN,ANIMATION_CLOSE,ANIMATION_FORCE_CLOSE
         */
        void sendAnimationMessage(int animation) {
            sendMessage(newMessage(animation, TYPE_FIRST));
        }

        /**
         * 送信するメッセージを生成します。
         *
         * @param animation ANIMATION_OPEN,ANIMATION_CLOSE,ANIMATION_FORCE_CLOSE
         * @param type      TYPE_FIRST,TYPE_UPDATE
         * @return Message
         */
        private static Message newMessage(int animation, int type) {
            final Message message = Message.obtain();
            message.what = animation;
            message.arg1 = type;
            return message;
        }

        /**
         * アニメーションが開始しているかどうかチェックします。
         *
         * @param animationCode アニメーションコード
         * @return アニメーションが開始していたらtrue.そうでなければfalse
         */
        boolean isAnimationStarted(int animationCode) {
            return mStartedCode == animationCode;
        }

        /**
         * 追従対象の位置情報を更新します。
         *
         * @param x 追従対象のX座標
         * @param y 追従対象のY座標
         */
        void updateTargetPosition(float x, float y) {
            mTargetPositionX = x;
            mTargetPositionY = y;
        }

        /**
         * Viewの表示状態が変更された際に呼び出されます。
         */
        void onUpdateViewLayout() {
            final TrashView trashView = mTrashView.get();
            if (trashView == null) {
                return;
            }
            // 削除アイコン(TrashIconRootView)の移動限界設定(Gravityの基準位置を元に計算）
            // 左下原点（画面下端（パディング含む）：0、上方向：マイナス、下方向：プラス）で、Y軸上限は削除アイコンが背景の中心に来る位置、下限はTrashIconRootViewが全部隠れる位置
            final float density = trashView.mMetrics.density;
            final float backgroundHeight = trashView.mBackgroundView.getMeasuredHeight();
            final float offsetX = TRASH_MOVE_LIMIT_OFFSET_X * density;
            final int trashIconHeight = trashView.mTrashIconRootView.getMeasuredHeight();
            final int left = (int) -offsetX;
            final int top = (int) ((trashIconHeight - backgroundHeight) / 2 - TRASH_MOVE_LIMIT_TOP_OFFSET * density);
            final int right = (int) offsetX;
            final int bottom = trashIconHeight;
            mTrashIconLimitPosition.set(left, top, right, bottom);

            // 背景の大きさをもとにY軸の追従範囲を設定
            mMoveStickyYRange = backgroundHeight * 0.20f;
        }
    }
}
