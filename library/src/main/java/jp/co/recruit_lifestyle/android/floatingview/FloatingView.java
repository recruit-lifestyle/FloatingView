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

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;

/**
 * フローティングViewを表すクラスです。
 * http://stackoverflow.com/questions/18503050/how-to-create-draggabble-system-alert-in-android
 * FIXME:Nexus5＋YouTubeアプリの場合にナビゲーションバーよりも前面に出てきてしまう
 */
class FloatingView extends FrameLayout implements ViewTreeObserver.OnPreDrawListener {

    /**
     * 移動に最低必要なしきい値(dp)
     */
    private static final float MOVE_THRESHOLD_DP = 8.0f;

    /**
     * 押下時の拡大率
     */
    private static final float SCALE_PRESSED = 0.9f;

    /**
     * 通常時の拡大率
     */
    private static final float SCALE_NORMAL = 1.0f;

    /**
     * 画面端移動アニメーションの時間
     */
    private static final long MOVE_TO_EDGE_DURATION = 450L;

    /**
     * 画面端移動アニメーションの係数
     */
    private static final float MOVE_TO_EDGE_OVERSHOOT_TENSION = 1.25f;

    /**
     * 通常状態
     */
    static final int STATE_NORMAL = 0;

    /**
     * 重なり状態
     */
    static final int STATE_INTERSECTING = 1;

    /**
     * 終了状態
     */
    static final int STATE_FINISHING = 2;

    /**
     * 長押し判定とする時間(移動操作も考慮して通常の1.5倍)
     */
    private static final int LONG_PRESS_TIMEOUT = (int) (1.5f * ViewConfiguration.getLongPressTimeout());

    /**
     * デフォルトのX座標を表す値
     */
    static final int DEFAULT_X = Integer.MIN_VALUE;

    /**
     * デフォルトのY座標を表す値
     */
    static final int DEFAULT_Y = Integer.MIN_VALUE;

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
     * 押下処理を通過しているかチェックするための時間
     */
    private long mTouchDownTime;

    /**
     * スクリーン押下X座標(移動量判定用)
     */
    private float mScreenTouchDownX;
    /**
     * スクリーン押下Y座標(移動量判定用)
     */
    private float mScreenTouchDownY;
    /**
     * 一度移動を始めたフラグ
     */
    private boolean mIsMoveAccept;

    /**
     * スクリーンのタッチX座標
     */
    private float mScreenTouchX;
    /**
     * スクリーンのタッチY座標
     */
    private float mScreenTouchY;
    /**
     * ローカルのタッチX座標
     */
    private float mLocalTouchX;
    /**
     * ローカルのタッチY座標
     */
    private float mLocalTouchY;
    /**
     * 初期表示のX座標
     */
    private int mInitX;
    /**
     * 初期表示のY座標
     */
    private int mInitY;

    /**
     * ステータスバーの高さ
     */
    private final int mStatusBarHeight;

    /**
     * 左・右端に寄せるアニメーション
     */
    private ValueAnimator mMoveEdgeAnimator;

    /**
     * Interpolator
     */
    private final TimeInterpolator mMoveEdgeInterpolator;

    /**
     * 移動限界を表すRect
     */
    private final Rect mMoveLimitRect;

    /**
     * 表示位置（画面端）の限界を表すRect
     */
    private final Rect mPositionLimitRect;

    /**
     * ドラッグ可能フラグ
     */
    private boolean mIsDraggable;

    /**
     * 形を表す係数
     */
    private float mShape;

    /**
     * FloatingViewのアニメーションを行うハンドラ
     */
    private final FloatingAnimationHandler mAnimationHandler;

    /**
     * 長押しを判定するためのハンドラ
     */
    private final LongPressHandler mLongPressHandler;

    /**
     * 画面端をオーバーするマージン
     */
    private int mOverMargin;

    /**
     * OnTouchListener
     */
    private OnTouchListener mOnTouchListener;

    /**
     * 長押し状態の場合
     */
    private boolean mIsLongPressed;

    /**
     * 移動方向
     */
    private int mMoveDirection;

    /**
     * コンストラクタ
     *
     * @param context {@link android.content.Context}
     */
    FloatingView(final Context context) {
        super(context);
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mParams = new WindowManager.LayoutParams();
        mMetrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(mMetrics);
        mParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        mParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mParams.type = WindowManager.LayoutParams.TYPE_PRIORITY_PHONE;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        mParams.format = PixelFormat.TRANSLUCENT;
        // 左下の座標を0とする
        mParams.gravity = Gravity.LEFT | Gravity.BOTTOM;
        mAnimationHandler = new FloatingAnimationHandler(this);
        mLongPressHandler = new LongPressHandler(this);
        mMoveEdgeInterpolator = new OvershootInterpolator(MOVE_TO_EDGE_OVERSHOOT_TENSION);
        mMoveDirection = FloatingViewManager.MOVE_DIRECTION_DEFAULT;

        mMoveLimitRect = new Rect();
        mPositionLimitRect = new Rect();

        // ステータスバーの高さを取得
        final Resources resources = context.getResources();
        final int statusBarHeightId = resources.getIdentifier("status_bar_height", "dimen", "android");
        if (statusBarHeightId > 0) {
            mStatusBarHeight = resources.getDimensionPixelSize(statusBarHeightId);
        } else {
            mStatusBarHeight = 0;
        }

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
     * 初回描画時の座標設定を行います。
     */
    @Override
    public boolean onPreDraw() {
        getViewTreeObserver().removeOnPreDrawListener(this);
        // 画面端に移動しない場合は指定座標に移動
        if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_NONE) {
            mParams.x = mInitX;
            mParams.y = mInitY;
            moveTo(mInitX, mInitY, mInitX, mInitY, false);
        } else {
            mParams.x = 0;
            mParams.y = mMetrics.heightPixels - mStatusBarHeight - getMeasuredHeight();
            moveToEdge(false);
        }
        mIsDraggable = true;
        mWindowManager.updateViewLayout(this, mParams);
        return true;
    }

    /**
     * 画面サイズから自位置を決定します。
     */
    private void updateViewLayout() {
        cancelAnimation();

        // 前の画面座標を保存
        final int oldScreenHeight = mMetrics.heightPixels;
        final int oldScreenWidth = mMetrics.widthPixels;
        final int oldPositionLimitWidth = mPositionLimitRect.width();
        final int oldPositionLimitHeight = mPositionLimitRect.height();

        // 新しい座標情報に切替
        mWindowManager.getDefaultDisplay().getMetrics(mMetrics);
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        final int newScreenWidth = mMetrics.widthPixels;
        final int newScreenHeight = mMetrics.heightPixels;

        // 移動範囲の設定
        mMoveLimitRect.set(-width, -height * 2, newScreenWidth + width, newScreenHeight + height);
        mPositionLimitRect.set(-mOverMargin, 0, newScreenWidth - width + mOverMargin, newScreenHeight - mStatusBarHeight - height);

        // 縦横切替の場合
        if (oldScreenWidth != newScreenWidth || oldScreenHeight != newScreenHeight) {
            // 画面端に移動する場合は現在の位置から左右端を設定
            if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_DEFAULT) {
                // 右半分にある場合
                if (mParams.x > (newScreenWidth - width) / 2) {
                    mParams.x = mPositionLimitRect.right;
                }
                // 左半分にある場合
                else {
                    mParams.x = mPositionLimitRect.left;
                }
            }
            // 左端に移動
            else if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_LEFT) {
                mParams.x = mPositionLimitRect.left;
            }
            // 右端に移動
            else if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_RIGHT) {
                mParams.x = mPositionLimitRect.right;
            }
            // 画面端に移動しない場合は画面座標の比率から計算
            else {
                final int newX = (int) (mParams.x * mPositionLimitRect.width() / (float) oldPositionLimitWidth + 0.5f);
                mParams.x = Math.min(Math.max(mPositionLimitRect.left, newX), mPositionLimitRect.right);
            }

            // スクリーン位置の比率からY座標を設定(四捨五入)
            final int newY = (int) (mParams.y * mPositionLimitRect.height() / (float) oldPositionLimitHeight + 0.5f);
            mParams.y = Math.min(Math.max(mPositionLimitRect.top, newY), mPositionLimitRect.bottom);
            mWindowManager.updateViewLayout(this, mParams);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDetachedFromWindow() {
        if (mMoveEdgeAnimator != null) {
            mMoveEdgeAnimator.removeAllUpdateListeners();
        }
        super.onDetachedFromWindow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        // Viewが表示されていなければ何もしない
        if (getVisibility() != View.VISIBLE) {
            return true;
        }

        // タッチ不能な場合は何もしない
        if (!mIsDraggable) {
            return true;
        }

        // 現在位置のキャッシュ
        mScreenTouchX = event.getRawX();
        mScreenTouchY = event.getRawY();
        final int action = event.getAction();
        // 押下
        if (action == MotionEvent.ACTION_DOWN) {
            // アニメーションのキャンセル
            cancelAnimation();
            mScreenTouchDownX = mScreenTouchX;
            mScreenTouchDownY = mScreenTouchY;
            mLocalTouchX = event.getX();
            mLocalTouchY = event.getY();
            mIsMoveAccept = false;
            setScale(SCALE_PRESSED);
            // タッチトラッキングアニメーションの開始
            mAnimationHandler.updateTouchPosition(getXByTouch(), getYByTouch());
            mAnimationHandler.removeMessages(FloatingAnimationHandler.ANIMATION_IN_TOUCH);
            mAnimationHandler.sendAnimationMessage(FloatingAnimationHandler.ANIMATION_IN_TOUCH);
            // 長押し判定の開始
            mLongPressHandler.removeMessages(LongPressHandler.LONG_PRESSED);
            mLongPressHandler.sendEmptyMessageDelayed(LongPressHandler.LONG_PRESSED, LONG_PRESS_TIMEOUT);
            // 押下処理の通過判定のための時間保持
            // mIsDraggableやgetVisibility()のフラグが押下後に変更された場合にMOVE等を処理させないようにするため
            mTouchDownTime = event.getDownTime();
        }
        // 移動
        else if (action == MotionEvent.ACTION_MOVE) {
            // 移動判定の場合は長押しの解除
            if (mIsMoveAccept) {
                mIsLongPressed = false;
                mLongPressHandler.removeMessages(LongPressHandler.LONG_PRESSED);
            }
            // 押下処理が行われていない場合は処理しない
            if (mTouchDownTime != event.getDownTime()) {
                return true;
            }
            final float moveThreshold = MOVE_THRESHOLD_DP * mMetrics.density;
            // 移動受付状態でない、かつX,Y軸ともにしきい値よりも小さい場合
            if (!mIsMoveAccept && Math.abs(mScreenTouchX - mScreenTouchDownX) < moveThreshold && Math.abs(mScreenTouchY - mScreenTouchDownY) < moveThreshold) {
                return true;
            }
            mIsMoveAccept = true;
            mAnimationHandler.updateTouchPosition(getXByTouch(), getYByTouch());
        }
        // 押上、キャンセル
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // 判定のため長押しの状態を一時的に保持
            final boolean tmpIsLongPressed = mIsLongPressed;
            // 長押しの解除
            mIsLongPressed = false;
            mLongPressHandler.removeMessages(LongPressHandler.LONG_PRESSED);
            // 押下処理が行われていない場合は処理しない
            if (mTouchDownTime != event.getDownTime()) {
                return true;
            }
            // アニメーションの削除
            mAnimationHandler.removeMessages(FloatingAnimationHandler.ANIMATION_IN_TOUCH);
            // 拡大率をもとに戻す
            setScale(SCALE_NORMAL);

            // 動かされていれば画面端に戻す
            if (mIsMoveAccept) {
                moveToEdge(true);
            }
            // 動かされていなければ、クリックイベントを発行
            else {
                if (!tmpIsLongPressed) {
                    final int size = getChildCount();
                    for (int i = 0; i < size; i++) {
                        getChildAt(i).performClick();
                    }
                }
            }
        }

        // タッチリスナを通知
        if (mOnTouchListener != null) {
            mOnTouchListener.onTouch(this, event);
        }

        return true;
    }

    /**
     * 長押しされた場合の処理です。
     */
    private void onLongClick() {
        mIsLongPressed = true;
        // 長押し処理
        final int size = getChildCount();
        for (int i = 0; i < size; i++) {
            getChildAt(i).performLongClick();
        }
    }

    /**
     * 画面から消す際の処理を表します。
     */
    @Override
    public void setVisibility(int visibility) {
        // 画面表示時
        if (visibility != View.VISIBLE) {
            // 画面から消す時は長押しをキャンセルし、画面端に強制的に移動します。
            cancelLongPress();
            setScale(SCALE_NORMAL);
            if (mIsMoveAccept) {
                moveToEdge(false);
            }
            mAnimationHandler.removeMessages(FloatingAnimationHandler.ANIMATION_IN_TOUCH);
            mLongPressHandler.removeMessages(LongPressHandler.LONG_PRESSED);
        }
        super.setVisibility(visibility);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOnTouchListener(OnTouchListener listener) {
        mOnTouchListener = listener;
    }

    /**
     * 左右の端に移動します。
     *
     * @param withAnimation アニメーションを行う場合はtrue.行わない場合はfalse
     */
    private void moveToEdge(boolean withAnimation) {
        //TODO:縦軸の速度も考慮して斜めに行くようにする
        // X・Y座標と移動方向を設定
        final int currentX = getXByTouch();
        final int currentY = getYByTouch();
        final int goalPositionX;
        // 画面端に移動する場合は画面端の座標を設定
        if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_DEFAULT) {
            final boolean isMoveRightEdge = currentX > (mMetrics.widthPixels - getWidth()) / 2;
            goalPositionX = isMoveRightEdge ? mPositionLimitRect.right : mPositionLimitRect.left;
        }
        // 左端への移動
        else if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_LEFT) {
            goalPositionX = mPositionLimitRect.left;
        }
        // 右端への移動
        else if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_RIGHT) {
            goalPositionX = mPositionLimitRect.right;
        }
        // 画面端に移動しない場合は、現在の座標のまま
        else {
            goalPositionX = currentX;
        }
        // TODO:Y座標もアニメーションさせる
        final int goalPositionY = currentY;
        // 指定座標に移動
        moveTo(currentX, currentY, goalPositionX, goalPositionY, withAnimation);
    }

    /**
     * 指定座標に移動します。<br/>
     * 画面端の座標を超える場合は、自動的に画面端に移動します。
     *
     * @param currentX      現在のX座標（アニメーションの始点用に使用）
     * @param currentY      現在のY座標（アニメーションの始点用に使用）
     * @param goalPositionX 移動先のX座標
     * @param goalPositionY 移動先のY座標
     * @param withAnimation アニメーションを行う場合はtrue.行わない場合はfalse
     */
    private void moveTo(int currentX, int currentY, int goalPositionX, int goalPositionY, boolean withAnimation) {
        // 画面端からはみ出さないように調整
        goalPositionX = Math.min(Math.max(mPositionLimitRect.left, goalPositionX), mPositionLimitRect.right);
        goalPositionY = Math.min(Math.max(mPositionLimitRect.top, goalPositionY), mPositionLimitRect.bottom);
        // アニメーションを行う場合
        if (withAnimation) {
            // TODO:Y座標もアニメーションさせる
            mParams.y = goalPositionY;

            mMoveEdgeAnimator = ValueAnimator.ofInt(currentX, goalPositionX);
            mMoveEdgeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mParams.x = (Integer) animation.getAnimatedValue();
                    mWindowManager.updateViewLayout(FloatingView.this, mParams);
                }
            });
            // X軸のアニメーション設定
            mMoveEdgeAnimator.setDuration(MOVE_TO_EDGE_DURATION);
            mMoveEdgeAnimator.setInterpolator(mMoveEdgeInterpolator);
            mMoveEdgeAnimator.start();
        } else {
            // 位置が変化した時のみ更新
            if (mParams.x != goalPositionX || mParams.y != goalPositionY) {
                mParams.x = goalPositionX;
                mParams.y = goalPositionY;
                mWindowManager.updateViewLayout(FloatingView.this, mParams);
            }
        }
        // タッチ座標を初期化
        mLocalTouchX = 0;
        mLocalTouchY = 0;
        mScreenTouchDownX = 0;
        mScreenTouchDownY = 0;
        mIsMoveAccept = false;
    }

    /**
     * アニメーションをキャンセルします。
     */
    private void cancelAnimation() {
        if (mMoveEdgeAnimator != null && mMoveEdgeAnimator.isStarted()) {
            mMoveEdgeAnimator.cancel();
            mMoveEdgeAnimator = null;
        }
    }

    /**
     * 拡大・縮小を行います。
     *
     * @param newScale 設定する拡大率
     */
    private void setScale(float newScale) {
        // INFO:childにscaleを設定しないと拡大率が変わらない現象に対処するための修正
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View targetView = getChildAt(i);
                targetView.setScaleX(newScale);
                targetView.setScaleY(newScale);
            }
        } else {
            setScaleX(newScale);
            setScaleY(newScale);
        }
    }

    /**
     * ドラッグ可能フラグ
     *
     * @param isDraggable ドラッグ可能にする場合はtrue
     */
    void setDraggable(boolean isDraggable) {
        mIsDraggable = isDraggable;
    }

    /**
     * Viewの形を表す定数
     *
     * @param shape SHAPE_CIRCLE or SHAPE_RECTANGLE
     */
    void setShape(float shape) {
        mShape = shape;
    }

    /**
     * Viewの形を取得します。
     *
     * @return SHAPE_CIRCLE or SHAPE_RECTANGLE
     */
    float getShape() {
        return mShape;
    }

    /**
     * 画面端をオーバーするマージンです。
     *
     * @param margin マージン
     */
    void setOverMargin(int margin) {
        mOverMargin = margin;
    }

    /**
     * 移動方向を設定します。
     *
     * @param moveDirection 移動方向
     */
    void setMoveDirection(int moveDirection) {
        // デフォルトから変更されていたら画面端に移動しない
        if (mInitX != DEFAULT_X || mInitY != DEFAULT_Y) {
            mMoveDirection = FloatingViewManager.MOVE_DIRECTION_NONE;
        } else {
            mMoveDirection = moveDirection;
        }
    }

    /**
     * 初期座標を設定します。
     *
     * @param x FloatingViewの初期X座標
     * @param y FloatingViewの初期Y座標
     */
    void setInitCoords(int x, int y) {
        mInitX = x;
        mInitY = y;
    }

    /**
     * Window上での描画領域を取得します。
     *
     * @param outRect 変更を加えるRect
     */
    void getWindowDrawingRect(Rect outRect) {
        final int currentX = getXByTouch();
        final int currentY = getYByTouch();
        outRect.set(currentX, currentY, currentX + getWidth(), currentY + getHeight());
    }

    /**
     * WindowManager.LayoutParamsを取得します。
     */
    WindowManager.LayoutParams getWindowLayoutParams() {
        return mParams;
    }

    /**
     * タッチ座標から算出されたFloatingViewのX座標
     *
     * @return FloatingViewのX座標
     */
    private int getXByTouch() {
        return (int) (mScreenTouchX - mLocalTouchX);
    }

    /**
     * タッチ座標から算出されたFloatingViewのY座標
     *
     * @return FloatingViewのY座標
     */
    private int getYByTouch() {
        return (int) (mMetrics.heightPixels - (mScreenTouchY - mLocalTouchY + getHeight()));
    }

    /**
     * 通常状態に変更します。
     */
    void setNormal() {
        mAnimationHandler.setState(STATE_NORMAL);
        mAnimationHandler.updateTouchPosition(getXByTouch(), getYByTouch());
    }

    /**
     * 重なった状態に変更します。
     *
     * @param centerX 対象の中心座標X
     * @param centerY 対象の中心座標Y
     */
    void setIntersecting(int centerX, int centerY) {
        mAnimationHandler.setState(STATE_INTERSECTING);
        mAnimationHandler.updateTargetPosition(centerX, centerY);
    }

    /**
     * 終了状態に変更します。
     */
    void setFinishing() {
        mAnimationHandler.setState(STATE_FINISHING);
        setVisibility(View.GONE);
    }

    int getState() {
        return mAnimationHandler.getState();
    }

    /**
     * アニメーションの制御を行うハンドラです。
     */
    static class FloatingAnimationHandler extends Handler {

        /**
         * アニメーションをリフレッシュするミリ秒
         */
        private static final long ANIMATION_REFRESH_TIME_MILLIS = 17L;

        /**
         * FloatingViewの吸着の着脱時間
         */
        private static final long CAPTURE_DURATION_MILLIS = 300L;

        /**
         * アニメーションなしの状態を表す定数
         */
        private static final int ANIMATION_NONE = 0;

        /**
         * タッチ時に発生するアニメーションの定数
         */
        private static final int ANIMATION_IN_TOUCH = 1;

        /**
         * アニメーション開始を表す定数
         */
        private static final int TYPE_FIRST = 1;
        /**
         * アニメーション更新を表す定数
         */
        private static final int TYPE_UPDATE = 2;

        /**
         * アニメーションを開始した時間
         */
        private long mStartTime;

        /**
         * アニメーションを始めた時点のTransitionX
         */
        private float mStartX;

        /**
         * アニメーションを始めた時点のTransitionY
         */
        private float mStartY;

        /**
         * 実行中のアニメーションのコード
         */
        private int mStartedCode;

        /**
         * アニメーション状態フラグ
         */
        private int mState;

        /**
         * 現在の状態
         */
        private boolean mIsChangeState;

        /**
         * 追従対象のX座標
         */
        private float mTouchPositionX;

        /**
         * 追従対象のY座標
         */
        private float mTouchPositionY;

        /**
         * 追従対象のX座標
         */
        private float mTargetPositionX;

        /**
         * 追従対象のY座標
         */
        private float mTargetPositionY;

        /**
         * FloatingView
         */
        private final WeakReference<FloatingView> mFloatingView;

        /**
         * コンストラクタ
         */
        FloatingAnimationHandler(FloatingView floatingView) {
            mFloatingView = new WeakReference<>(floatingView);
            mStartedCode = ANIMATION_NONE;
            mState = STATE_NORMAL;
        }

        /**
         * アニメーションの処理を行います。
         */
        @Override
        public void handleMessage(Message msg) {
            final FloatingView floatingView = mFloatingView.get();
            if (floatingView == null) {
                removeMessages(ANIMATION_IN_TOUCH);
                return;
            }

            final int animationCode = msg.what;
            final int animationType = msg.arg1;
            final WindowManager.LayoutParams params = floatingView.mParams;
            final WindowManager windowManager = floatingView.mWindowManager;

            // 状態変更またはアニメーションを開始した場合の初期化
            if (mIsChangeState || animationType == TYPE_FIRST) {
                // 状態変更時のみアニメーション時間を使う
                mStartTime = mIsChangeState ? SystemClock.uptimeMillis() : 0;
                mStartX = params.x;
                mStartY = params.y;
                mStartedCode = animationCode;
                mIsChangeState = false;
            }
            // 経過時間
            final float elapsedTime = SystemClock.uptimeMillis() - mStartTime;
            final float trackingTargetTimeRate = Math.min(elapsedTime / CAPTURE_DURATION_MILLIS, 1.0f);

            // 重なっていない場合のアニメーション
            if (mState == FloatingView.STATE_NORMAL) {
                final float basePosition = calcAnimationPosition(trackingTargetTimeRate);
                // 画面外へのオーバーを認める
                final Rect moveLimitRect = floatingView.mMoveLimitRect;
                // 最終的な到達点
                final float targetPositionX = Math.min(Math.max(moveLimitRect.left, (int) mTouchPositionX), moveLimitRect.right);
                final float targetPositionY = Math.min(Math.max(moveLimitRect.top, (int) mTouchPositionY), moveLimitRect.bottom);
                params.x = (int) (mStartX + (targetPositionX - mStartX) * basePosition);
                params.y = (int) (mStartY + (targetPositionY - mStartY) * basePosition);
                windowManager.updateViewLayout(floatingView, params);
                sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS);
            }
            // 重なった場合のアニメーション
            else if (mState == FloatingView.STATE_INTERSECTING) {
                final float basePosition = calcAnimationPosition(trackingTargetTimeRate);
                // 最終的な到達点
                final float targetPositionX = mTargetPositionX - floatingView.getWidth() / 2;
                final float targetPositionY = mTargetPositionY - floatingView.getHeight() / 2;
                // 現在地からの移動
                params.x = (int) (mStartX + (targetPositionX - mStartX) * basePosition);
                params.y = (int) (mStartY + (targetPositionY - mStartY) * basePosition);
                windowManager.updateViewLayout(floatingView, params);
                sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS);
            }

        }

        /**
         * アニメーション時間から求められる位置を計算します。
         *
         * @param timeRate 時間比率
         * @return ベースとなる係数(0.0から1.0＋α)
         */
        private static float calcAnimationPosition(float timeRate) {
            final float position;
            // y=0.55sin(8.0564x-π/2)+0.55
            if (timeRate <= 0.4) {
                position = (float) (0.55 * Math.sin(8.0564 * timeRate - Math.PI / 2) + 0.55);
            }
            // y=4(0.417x-0.341)^2-4(0.417-0.341)^2+1
            else {
                position = (float) (4 * Math.pow(0.417 * timeRate - 0.341, 2) - 4 * Math.pow(0.417 - 0.341, 2) + 1);
            }
            return position;
        }

        /**
         * アニメーションのメッセージを送信します。
         *
         * @param animation   ANIMATION_IN_TOUCH
         * @param delayMillis メッセージの送信時間
         */
        void sendAnimationMessageDelayed(int animation, long delayMillis) {
            sendMessageAtTime(newMessage(animation, TYPE_FIRST), SystemClock.uptimeMillis() + delayMillis);
        }

        /**
         * アニメーションのメッセージを送信します。
         *
         * @param animation ANIMATION_IN_TOUCH
         */
        void sendAnimationMessage(int animation) {
            sendMessage(newMessage(animation, TYPE_FIRST));
        }

        /**
         * 送信するメッセージを生成します。
         *
         * @param animation ANIMATION_IN_TOUCH
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
         * タッチ座標の位置を更新します。
         *
         * @param positionX タッチX座標
         * @param positionY タッチY座標
         */
        void updateTouchPosition(float positionX, float positionY) {
            mTouchPositionX = positionX;
            mTouchPositionY = positionY;
        }

        /**
         * 追従対象の位置を更新します。
         *
         * @param centerX 追従対象のX座標
         * @param centerY 追従対象のY座標
         */
        void updateTargetPosition(float centerX, float centerY) {
            mTargetPositionX = centerX;
            mTargetPositionY = centerY;
        }

        /**
         * アニメーション状態を設定します。
         *
         * @param newState STATE_NORMAL or STATE_INTERSECTING or STATE_FINISHING
         */
        void setState(int newState) {
            // 状態が異なった場合のみ状態を変更フラグを変える
            if (mState != newState) {
                mIsChangeState = true;
            }
            mState = newState;
        }

        /**
         * 現在の状態を返します。
         *
         * @return STATE_NORMAL or STATE_INTERSECTING or STATE_FINISHING
         */
        int getState() {
            return mState;
        }
    }

    /**
     * 長押し処理を制御するハンドラです。<br/>
     * dispatchTouchEventで全てのタッチ処理を実装しているので、長押しも独自実装しています。
     */
    static class LongPressHandler extends Handler {

        /**
         * TrashView
         */
        private WeakReference<FloatingView> mFloatingView;

        /**
         * アニメーションなしの状態を表す定数
         */
        private static final int LONG_PRESSED = 0;

        /**
         * コンストラクタ
         *
         * @param view FloatingView
         */
        LongPressHandler(FloatingView view) {
            mFloatingView = new WeakReference<>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            FloatingView view = mFloatingView.get();
            if (view == null) {
                removeMessages(LONG_PRESSED);
                return;
            }

            view.onLongClick();
        }
    }
}
