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
import android.support.animation.DynamicAnimation;
import android.support.animation.FlingAnimation;
import android.support.animation.FloatValueHolder;
import android.support.animation.SpringAnimation;
import android.support.animation.SpringForce;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

/**
 * フローティングViewを表すクラスです。
 * http://stackoverflow.com/questions/18503050/how-to-create-draggabble-system-alert-in-android
 * FIXME:Nexus5＋YouTubeアプリの場合にナビゲーションバーよりも前面に出てきてしまう
 */
class FloatingView extends FrameLayout implements ViewTreeObserver.OnPreDrawListener {

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
     * Damping ratio constant for spring animation (X coordinate)
     */
    private static final float ANIMATION_SPRING_X_DAMPING_RATIO = 0.7f;

    /**
     * Stiffness constant for spring animation (X coordinate)
     */
    private static final float ANIMATION_SPRING_X_STIFFNESS = 350f;

    /**
     * Friction constant for fling animation (X coordinate)
     */
    private static final float ANIMATION_FLING_X_FRICTION = 1.7f;

    /**
     * Friction constant for fling animation (Y coordinate)
     */
    private static final float ANIMATION_FLING_Y_FRICTION = 1.7f;

    /**
     * Current velocity units
     */
    private static final int CURRENT_VELOCITY_UNITS = 1000;

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
     * AnimationState
     */
    @IntDef({STATE_NORMAL, STATE_INTERSECTING, STATE_FINISHING})
    @Retention(RetentionPolicy.SOURCE)
    @interface AnimationState {
    }

    /**
     * 長押し判定とする時間(移動操作も考慮して通常の1.5倍)
     */
    private static final int LONG_PRESS_TIMEOUT = (int) (1.5f * ViewConfiguration.getLongPressTimeout());

    /**
     * Constant for scaling down X coordinate velocity
     */
    private static final float MAX_X_VELOCITY_SCALE_DOWN_VALUE = 9;

    /**
     * Constant for scaling down Y coordinate velocity
     */
    private static final float MAX_Y_VELOCITY_SCALE_DOWN_VALUE = 8;

    /**
     * Constant for calculating the threshold to move when throwing
     */
    private static final float THROW_THRESHOLD_SCALE_DOWN_VALUE = 9;

    /**
     * デフォルトのX座標を表す値
     */
    static final int DEFAULT_X = Integer.MIN_VALUE;

    /**
     * デフォルトのY座標を表す値
     */
    static final int DEFAULT_Y = Integer.MIN_VALUE;

    /**
     * Default width size
     */
    static final int DEFAULT_WIDTH = ViewGroup.LayoutParams.WRAP_CONTENT;

    /**
     * Default height size
     */
    static final int DEFAULT_HEIGHT = ViewGroup.LayoutParams.WRAP_CONTENT;

    /**
     * Overlay Type
     */
    private static final int OVERLAY_TYPE;

    /**
     * WindowManager
     */
    private final WindowManager mWindowManager;

    /**
     * LayoutParams
     */
    private final WindowManager.LayoutParams mParams;

    /**
     * VelocityTracker
     */
    private VelocityTracker mVelocityTracker;

    /**
     * {@link ViewConfiguration}
     */
    private ViewConfiguration mViewConfiguration;

    /**
     * Minimum threshold required for movement(px)
     */
    private float mMoveThreshold;

    /**
     * Maximum fling velocity
     */
    private float mMaximumFlingVelocity;

    /**
     * Maximum x coordinate velocity
     */
    private float mMaximumXVelocity;

    /**
     * Maximum x coordinate velocity
     */
    private float mMaximumYVelocity;

    /**
     * Threshold to move when throwing
     */
    private float mThrowMoveThreshold;

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
     * Initial animation running flag
     */
    private boolean mIsInitialAnimationRunning;

    /**
     * 初期表示時にアニメーションするフラグ
     */
    private boolean mAnimateInitialMove;

    /**
     * status bar's height
     */
    private final int mBaseStatusBarHeight;

    /**
     * status bar's height(landscape)
     */
    private final int mBaseStatusBarRotatedHeight;

    /**
     * Current status bar's height
     */
    private int mStatusBarHeight;

    /**
     * Navigation bar's height(portrait)
     */
    private final int mBaseNavigationBarHeight;

    /**
     * Navigation bar's height
     * Placed bottom on the screen(tablet)
     * Or placed vertically on the screen(phone)
     */
    private final int mBaseNavigationBarRotatedHeight;

    /**
     * Current Navigation bar's vertical size
     */
    private int mNavigationBarVerticalOffset;

    /**
     * Current Navigation bar's horizontal size
     */
    private int mNavigationBarHorizontalOffset;

    /**
     * Offset of touch X coordinate
     */
    private int mTouchXOffset;

    /**
     * Offset of touch Y coordinate
     */
    private int mTouchYOffset;

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
     * Use dynamic physics-based animations or not
     */
    private boolean mUsePhysics;

    /**
     * If true, it's a tablet. If false, it's a phone
     */
    private final boolean mIsTablet;

    /**
     * Surface.ROTATION_XXX
     */
    private int mRotation;

    /**
     * Cutout safe inset rect(Same as FloatingViewManager's mSafeInsetRect)
     */
    private final Rect mSafeInsetRect;

    static {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            OVERLAY_TYPE = WindowManager.LayoutParams.TYPE_PRIORITY_PHONE;
        } else {
            OVERLAY_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
    }

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
        mParams.type = OVERLAY_TYPE;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        mParams.format = PixelFormat.TRANSLUCENT;
        // 左下の座標を0とする
        mParams.gravity = Gravity.LEFT | Gravity.BOTTOM;
        mAnimationHandler = new FloatingAnimationHandler(this);
        mLongPressHandler = new LongPressHandler(this);
        mMoveEdgeInterpolator = new OvershootInterpolator(MOVE_TO_EDGE_OVERSHOOT_TENSION);
        mMoveDirection = FloatingViewManager.MOVE_DIRECTION_DEFAULT;
        mUsePhysics = false;
        final Resources resources = context.getResources();
        mIsTablet = (resources.getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
        mRotation = mWindowManager.getDefaultDisplay().getRotation();

        mMoveLimitRect = new Rect();
        mPositionLimitRect = new Rect();
        mSafeInsetRect = new Rect();

        // ステータスバーの高さを取得
        mBaseStatusBarHeight = getSystemUiDimensionPixelSize(resources, "status_bar_height");
        // Check landscape resource id
        final int statusBarLandscapeResId = resources.getIdentifier("status_bar_height_landscape", "dimen", "android");
        if (statusBarLandscapeResId > 0) {
            mBaseStatusBarRotatedHeight = getSystemUiDimensionPixelSize(resources, "status_bar_height_landscape");
        } else {
            mBaseStatusBarRotatedHeight = mBaseStatusBarHeight;
        }

        // Init physics-based animation properties
        updateViewConfiguration();

        // Detect NavigationBar
        if (hasSoftNavigationBar()) {
            mBaseNavigationBarHeight = getSystemUiDimensionPixelSize(resources, "navigation_bar_height");
            final String resName = mIsTablet ? "navigation_bar_height_landscape" : "navigation_bar_width";
            mBaseNavigationBarRotatedHeight = getSystemUiDimensionPixelSize(resources, resName);
        } else {
            mBaseNavigationBarHeight = 0;
            mBaseNavigationBarRotatedHeight = 0;
        }

        // 初回描画処理用
        getViewTreeObserver().addOnPreDrawListener(this);
    }

    /**
     * Check if there is a software navigation bar(including the navigation bar in the screen).
     *
     * @return True if there is a software navigation bar
     */
    private boolean hasSoftNavigationBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            final DisplayMetrics realDisplayMetrics = new DisplayMetrics();
            mWindowManager.getDefaultDisplay().getRealMetrics(realDisplayMetrics);
            return realDisplayMetrics.heightPixels > mMetrics.heightPixels || realDisplayMetrics.widthPixels > mMetrics.widthPixels;
        }

        // old device check flow
        // Navigation bar exists (config_showNavigationBar is true, or both the menu key and the back key are not exists)
        final Context context = getContext();
        final Resources resources = context.getResources();
        final boolean hasMenuKey = ViewConfiguration.get(context).hasPermanentMenuKey();
        final boolean hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);
        final int showNavigationBarResId = resources.getIdentifier("config_showNavigationBar", "bool", "android");
        final boolean hasNavigationBarConfig = showNavigationBarResId != 0 && resources.getBoolean(showNavigationBarResId);
        return hasNavigationBarConfig || (!hasMenuKey && !hasBackKey);
    }

    /**
     * Get the System ui dimension(pixel)
     *
     * @param resources {@link Resources}
     * @param resName   dimension resource name
     * @return pixel size
     */
    private static int getSystemUiDimensionPixelSize(Resources resources, String resName) {
        int pixelSize = 0;
        final int resId = resources.getIdentifier(resName, "dimen", "android");
        if (resId > 0) {
            pixelSize = resources.getDimensionPixelSize(resId);
        }
        return pixelSize;
    }


    /**
     * 表示位置を決定します。
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        refreshLimitRect();
    }

    /**
     * 画面回転時にレイアウトの調整をします。
     */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateViewConfiguration();
        refreshLimitRect();
    }

    /**
     * 初回描画時の座標設定を行います。
     */
    @Override
    public boolean onPreDraw() {
        getViewTreeObserver().removeOnPreDrawListener(this);
        // X座標に初期値が設定されていればデフォルト値を入れる(マージンは考慮しない)
        if (mInitX == DEFAULT_X) {
            mInitX = 0;
        }
        // Y座標に初期値が設定されていればデフォルト値を入れる
        if (mInitY == DEFAULT_Y) {
            mInitY = mMetrics.heightPixels - mStatusBarHeight - getMeasuredHeight();
        }

        // 初期位置を設定
        mParams.x = mInitX;
        mParams.y = mInitY;

        // 画面端に移動しない場合は指定座標に移動
        if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_NONE) {
            moveTo(mInitX, mInitY, mInitX, mInitY, false);
        } else {
            mIsInitialAnimationRunning = true;
            // 初期位置から画面端に移動
            moveToEdge(mInitX, mInitY, mAnimateInitialMove);
        }
        mIsDraggable = true;
        updateViewLayout();
        return true;
    }

    /**
     * Called when the layout of the system has changed.
     *
     * @param isHideStatusBar     If true, the status bar is hidden
     * @param isHideNavigationBar If true, the navigation bar is hidden
     * @param isPortrait          If true, the device orientation is portrait
     * @param windowRect          {@link Rect} of system window
     */
    void onUpdateSystemLayout(boolean isHideStatusBar, boolean isHideNavigationBar, boolean isPortrait, Rect windowRect) {
        // status bar
        updateStatusBarHeight(isHideStatusBar, isPortrait);
        // touch X offset(support Cutout)
        updateTouchXOffset(isHideNavigationBar, windowRect.left);
        // touch Y offset(support Cutout)
        mTouchYOffset = isPortrait ? mSafeInsetRect.top : 0;
        // navigation bar
        updateNavigationBarOffset(isHideNavigationBar, isPortrait, windowRect);
        refreshLimitRect();
    }

    /**
     * Update height of StatusBar.
     *
     * @param isHideStatusBar If true, the status bar is hidden
     * @param isPortrait      If true, the device orientation is portrait
     */
    private void updateStatusBarHeight(boolean isHideStatusBar, boolean isPortrait) {
        if (isHideStatusBar) {
            // 1.(No Cutout)No StatusBar(=0)
            // 2.(Has Cutout)StatusBar is not included in mMetrics.heightPixels (=0)
            mStatusBarHeight = 0;
            return;
        }

        // Has Cutout
        final boolean hasTopCutout = mSafeInsetRect.top != 0;
        if (hasTopCutout) {
            if (isPortrait) {
                mStatusBarHeight = 0;
            } else {
                mStatusBarHeight = mBaseStatusBarRotatedHeight;
            }
            return;
        }

        // No cutout
        if (isPortrait) {
            mStatusBarHeight = mBaseStatusBarHeight;
        } else {
            mStatusBarHeight = mBaseStatusBarRotatedHeight;
        }
    }

    /**
     * Update of touch X coordinate
     *
     * @param isHideNavigationBar If true, the navigation bar is hidden
     * @param windowLeftOffset    Left side offset of device display
     */
    private void updateTouchXOffset(boolean isHideNavigationBar, int windowLeftOffset) {
        final boolean hasBottomCutout = mSafeInsetRect.bottom != 0;
        if (hasBottomCutout) {
            mTouchXOffset = windowLeftOffset;
            return;
        }

        // No cutout
        // touch X offset(navigation bar is displayed and it is on the left side of the device)
        mTouchXOffset = !isHideNavigationBar && windowLeftOffset > 0 ? mBaseNavigationBarRotatedHeight : 0;
    }

    /**
     * Update offset of NavigationBar.
     *
     * @param isHideNavigationBar If true, the navigation bar is hidden
     * @param isPortrait          If true, the device orientation is portrait
     * @param windowRect          {@link Rect} of system window
     */
    private void updateNavigationBarOffset(boolean isHideNavigationBar, boolean isPortrait, Rect windowRect) {
        int currentNavigationBarHeight = 0;
        int currentNavigationBarWidth = 0;
        int navigationBarVerticalDiff = 0;
        final boolean hasSoftNavigationBar = hasSoftNavigationBar();
        // auto hide navigation bar(Galaxy S8, S9 and so on.)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            final DisplayMetrics realDisplayMetrics = new DisplayMetrics();
            mWindowManager.getDefaultDisplay().getRealMetrics(realDisplayMetrics);
            currentNavigationBarHeight = realDisplayMetrics.heightPixels - windowRect.bottom;
            currentNavigationBarWidth = realDisplayMetrics.widthPixels - mMetrics.widthPixels;
            navigationBarVerticalDiff = mBaseNavigationBarHeight - currentNavigationBarHeight;
        }

        if (!isHideNavigationBar) {
            // auto hide navigation bar
            // 他デバイスとの矛盾をもとに推測する
            // 1.デバイスに組み込まれたナビゲーションバー（mBaseNavigationBarHeight == 0）はシステムの状態によって高さに差が発生しない
            // 2.デバイスに組み込まれたナビゲーションバー(!hasSoftNavigationBar)は意図的にBaseを0にしているので、矛盾している
            if (navigationBarVerticalDiff != 0 && mBaseNavigationBarHeight == 0 ||
                    !hasSoftNavigationBar && mBaseNavigationBarHeight != 0) {
                if (hasSoftNavigationBar) {
                    // 1.auto hide mode -> show mode
                    // 2.show mode -> auto hide mode -> home
                    mNavigationBarVerticalOffset = 0;
                } else {
                    // show mode -> home
                    mNavigationBarVerticalOffset = -currentNavigationBarHeight;
                }
            } else {
                // normal device
                mNavigationBarVerticalOffset = 0;
            }

            mNavigationBarHorizontalOffset = 0;
            return;
        }

        // If the portrait, is displayed at the bottom of the screen
        if (isPortrait) {
            // auto hide navigation bar
            if (!hasSoftNavigationBar && mBaseNavigationBarHeight != 0) {
                mNavigationBarVerticalOffset = 0;
            } else {
                mNavigationBarVerticalOffset = mBaseNavigationBarHeight;
            }
            mNavigationBarHorizontalOffset = 0;
            return;
        }

        // If it is a Tablet, it will appear at the bottom of the screen.
        // If it is Phone, it will appear on the side of the screen
        if (mIsTablet) {
            mNavigationBarVerticalOffset = mBaseNavigationBarRotatedHeight;
            mNavigationBarHorizontalOffset = 0;
        } else {
            mNavigationBarVerticalOffset = 0;
            // auto hide navigation bar
            // 他デバイスとの矛盾をもとに推測する
            // 1.デバイスに組み込まれたナビゲーションバー(!hasSoftNavigationBar)は、意図的にBaseを0にしているので、矛盾している
            if (!hasSoftNavigationBar && mBaseNavigationBarRotatedHeight != 0) {
                mNavigationBarHorizontalOffset = 0;
            } else if (hasSoftNavigationBar && mBaseNavigationBarRotatedHeight == 0) {
                // 2.ソフトナビゲーションバーの場合、Baseが設定されるため矛盾している
                mNavigationBarHorizontalOffset = currentNavigationBarWidth;
            } else {
                mNavigationBarHorizontalOffset = mBaseNavigationBarRotatedHeight;
            }
        }
    }

    /**
     * Update {@link ViewConfiguration}
     */
    private void updateViewConfiguration() {
        mViewConfiguration = ViewConfiguration.get(getContext());
        mMoveThreshold = mViewConfiguration.getScaledTouchSlop();
        mMaximumFlingVelocity = mViewConfiguration.getScaledMaximumFlingVelocity();
        mMaximumXVelocity = mMaximumFlingVelocity / MAX_X_VELOCITY_SCALE_DOWN_VALUE;
        mMaximumYVelocity = mMaximumFlingVelocity / MAX_Y_VELOCITY_SCALE_DOWN_VALUE;
        mThrowMoveThreshold = mMaximumFlingVelocity / THROW_THRESHOLD_SCALE_DOWN_VALUE;
    }

    /**
     * Update the PositionLimitRect and MoveLimitRect according to the screen size change.
     */
    private void refreshLimitRect() {
        cancelAnimation();

        // 前の画面座標を保存
        final int oldPositionLimitWidth = mPositionLimitRect.width();
        final int oldPositionLimitHeight = mPositionLimitRect.height();

        // 新しい座標情報に切替
        mWindowManager.getDefaultDisplay().getMetrics(mMetrics);
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        final int newScreenWidth = mMetrics.widthPixels;
        final int newScreenHeight = mMetrics.heightPixels;

        // 移動範囲の設定
        mMoveLimitRect.set(-width, -height * 2, newScreenWidth + width + mNavigationBarHorizontalOffset, newScreenHeight + height + mNavigationBarVerticalOffset);
        mPositionLimitRect.set(-mOverMargin, 0, newScreenWidth - width + mOverMargin + mNavigationBarHorizontalOffset, newScreenHeight - mStatusBarHeight - height + mNavigationBarVerticalOffset);

        // Initial animation stop when the device rotates
        final int newRotation = mWindowManager.getDefaultDisplay().getRotation();
        if (mAnimateInitialMove && mRotation != newRotation) {
            mIsInitialAnimationRunning = false;
        }

        // When animation is running and the device is not rotating
        if (mIsInitialAnimationRunning && mRotation == newRotation) {
            moveToEdge(mParams.x, mParams.y, true);
        } else {
            // If there is a screen change during the operation, move to the appropriate position
            if (mIsMoveAccept) {
                moveToEdge(mParams.x, mParams.y, false);
            } else {
                final int newX = (int) (mParams.x * mPositionLimitRect.width() / (float) oldPositionLimitWidth + 0.5f);
                final int goalPositionX = Math.min(Math.max(mPositionLimitRect.left, newX), mPositionLimitRect.right);
                final int newY = (int) (mParams.y * mPositionLimitRect.height() / (float) oldPositionLimitHeight + 0.5f);
                final int goalPositionY = Math.min(Math.max(mPositionLimitRect.top, newY), mPositionLimitRect.bottom);
                moveTo(mParams.x, mParams.y, goalPositionX, goalPositionY, false);
            }
        }
        mRotation = newRotation;
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

        // Block while initial display animation is running
        if (mIsInitialAnimationRunning) {
            return true;
        }

        // 現在位置のキャッシュ
        mScreenTouchX = event.getRawX();
        mScreenTouchY = event.getRawY();
        final int action = event.getAction();
        boolean isWaitForMoveToEdge = false;
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

            if (mVelocityTracker == null) {
                // Retrieve a new VelocityTracker object to watch the velocity of a motion.
                mVelocityTracker = VelocityTracker.obtain();
            } else {
                // Reset the velocity tracker back to its initial state.
                mVelocityTracker.clear();
            }

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
            // compute offset and restore
            addMovement(event);
            mIsInitialAnimationRunning = false;
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
            // 移動受付状態でない、かつX,Y軸ともにしきい値よりも小さい場合
            if (!mIsMoveAccept && Math.abs(mScreenTouchX - mScreenTouchDownX) < mMoveThreshold && Math.abs(mScreenTouchY - mScreenTouchDownY) < mMoveThreshold) {
                return true;
            }
            mIsMoveAccept = true;
            mAnimationHandler.updateTouchPosition(getXByTouch(), getYByTouch());
            // compute offset and restore
            addMovement(event);
        }
        // 押上、キャンセル
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // compute velocity tracker
            if (mVelocityTracker != null) {
                mVelocityTracker.computeCurrentVelocity(CURRENT_VELOCITY_UNITS);
            }

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

            // destroy VelocityTracker (#103)
            if (!mIsMoveAccept && mVelocityTracker != null) {
                mVelocityTracker.recycle();
                mVelocityTracker = null;
            }

            // When ACTION_UP is done (when not pressed or moved)
            if (action == MotionEvent.ACTION_UP && !tmpIsLongPressed && !mIsMoveAccept) {
                final int size = getChildCount();
                for (int i = 0; i < size; i++) {
                    getChildAt(i).performClick();
                }
            } else {
                // Make a move after checking whether it is finished or not
                isWaitForMoveToEdge = true;
            }
        }

        // タッチリスナを通知
        if (mOnTouchListener != null) {
            mOnTouchListener.onTouch(this, event);
        }

        // Lazy execution of moveToEdge
        if (isWaitForMoveToEdge && mAnimationHandler.getState() != STATE_FINISHING) {
            // include device rotation
            moveToEdge(true);
            if (mVelocityTracker != null) {
                mVelocityTracker.recycle();
                mVelocityTracker = null;
            }
        }

        return true;
    }

    /**
     * Call addMovement and restore MotionEvent coordinate
     *
     * @param event {@link MotionEvent}
     */
    private void addMovement(@NonNull MotionEvent event) {
        final float deltaX = event.getRawX() - event.getX();
        final float deltaY = event.getRawY() - event.getY();
        event.offsetLocation(deltaX, deltaY);
        mVelocityTracker.addMovement(event);
        event.offsetLocation(-deltaX, -deltaY);
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
        final int currentX = getXByTouch();
        final int currentY = getYByTouch();
        moveToEdge(currentX, currentY, withAnimation);
    }

    /**
     * 始点を指定して左右の端に移動します。
     *
     * @param startX        X座標の初期値
     * @param startY        Y座標の初期値
     * @param withAnimation アニメーションを行う場合はtrue.行わない場合はfalse
     */
    private void moveToEdge(int startX, int startY, boolean withAnimation) {
        // 指定座標に移動
        final int goalPositionX = getGoalPositionX(startX, startY);
        final int goalPositionY = getGoalPositionY(startX, startY);
        moveTo(startX, startY, goalPositionX, goalPositionY, withAnimation);
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
            // Use physics animation
            final boolean usePhysicsAnimation = mUsePhysics && mVelocityTracker != null && mMoveDirection != FloatingViewManager.MOVE_DIRECTION_NEAREST;
            if (usePhysicsAnimation) {
                startPhysicsAnimation(goalPositionX, currentY);
            } else {
                startObjectAnimation(currentX, currentY, goalPositionX, goalPositionY);
            }
        } else {
            // 位置が変化した時のみ更新
            if (mParams.x != goalPositionX || mParams.y != goalPositionY) {
                mParams.x = goalPositionX;
                mParams.y = goalPositionY;
                updateViewLayout();
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
     * Start Physics-based animation
     *
     * @param goalPositionX goal position X coordinate
     * @param currentY      current Y coordinate
     */
    private void startPhysicsAnimation(int goalPositionX, int currentY) {
        // start X coordinate animation
        final boolean containsLimitRectWidth = mParams.x < mPositionLimitRect.right && mParams.x > mPositionLimitRect.left;
        // If MOVE_DIRECTION_NONE, play fling animation
        if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_NONE && containsLimitRectWidth) {
            final float velocityX = Math.min(Math.max(mVelocityTracker.getXVelocity(), -mMaximumXVelocity), mMaximumXVelocity);
            startFlingAnimationX(velocityX);
        } else {
            startSpringAnimationX(goalPositionX);
        }

        // start Y coordinate animation
        final boolean containsLimitRectHeight = mParams.y < mPositionLimitRect.bottom && mParams.y > mPositionLimitRect.top;
        final float velocityY = -Math.min(Math.max(mVelocityTracker.getYVelocity(), -mMaximumYVelocity), mMaximumYVelocity);
        if (containsLimitRectHeight) {
            startFlingAnimationY(velocityY);
        } else {
            startSpringAnimationY(currentY, velocityY);
        }
    }

    /**
     * Start object animation
     *
     * @param currentX      current X coordinate
     * @param currentY      current Y coordinate
     * @param goalPositionX goal position X coordinate
     * @param goalPositionY goal position Y coordinate
     */
    private void startObjectAnimation(int currentX, int currentY, int goalPositionX, int goalPositionY) {
        if (goalPositionX == currentX) {
            //to move only y coord
            mMoveEdgeAnimator = ValueAnimator.ofInt(currentY, goalPositionY);
            mMoveEdgeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mParams.y = (Integer) animation.getAnimatedValue();
                    updateViewLayout();
                    updateInitAnimation(animation);
                }
            });
        } else {
            // To move only x coord (to left or right)
            mParams.y = goalPositionY;
            mMoveEdgeAnimator = ValueAnimator.ofInt(currentX, goalPositionX);
            mMoveEdgeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mParams.x = (Integer) animation.getAnimatedValue();
                    updateViewLayout();
                    updateInitAnimation(animation);
                }
            });
        }
        // X軸のアニメーション設定
        mMoveEdgeAnimator.setDuration(MOVE_TO_EDGE_DURATION);
        mMoveEdgeAnimator.setInterpolator(mMoveEdgeInterpolator);
        mMoveEdgeAnimator.start();
    }

    /**
     * Start spring animation(X coordinate)
     *
     * @param goalPositionX goal position X coordinate
     */
    private void startSpringAnimationX(int goalPositionX) {
        // springX
        final SpringForce springX = new SpringForce(goalPositionX);
        springX.setDampingRatio(ANIMATION_SPRING_X_DAMPING_RATIO);
        springX.setStiffness(ANIMATION_SPRING_X_STIFFNESS);
        // springAnimation
        final SpringAnimation springAnimationX = new SpringAnimation(new FloatValueHolder());
        springAnimationX.setStartVelocity(mVelocityTracker.getXVelocity());
        springAnimationX.setStartValue(mParams.x);
        springAnimationX.setSpring(springX);
        springAnimationX.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS);
        springAnimationX.addUpdateListener(new DynamicAnimation.OnAnimationUpdateListener() {
            @Override
            public void onAnimationUpdate(DynamicAnimation animation, float value, float velocity) {
                final int x = Math.round(value);
                // Not moving, or the touch operation is continuing
                if (mParams.x == x || mVelocityTracker != null) {
                    return;
                }
                // update x coordinate
                mParams.x = x;
                updateViewLayout();
            }
        });
        springAnimationX.start();
    }

    /**
     * Start spring animation(Y coordinate)
     *
     * @param currentY  current Y coordinate
     * @param velocityY velocity Y coordinate
     */
    private void startSpringAnimationY(int currentY, float velocityY) {
        // Create SpringForce
        final SpringForce springY = new SpringForce(currentY < mMetrics.heightPixels / 2 ? mPositionLimitRect.top : mPositionLimitRect.bottom);
        springY.setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY);
        springY.setStiffness(SpringForce.STIFFNESS_LOW);

        // Create SpringAnimation
        final SpringAnimation springAnimationY = new SpringAnimation(new FloatValueHolder());
        springAnimationY.setStartVelocity(velocityY);
        springAnimationY.setStartValue(mParams.y);
        springAnimationY.setSpring(springY);
        springAnimationY.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS);
        springAnimationY.addUpdateListener(new DynamicAnimation.OnAnimationUpdateListener() {
            @Override
            public void onAnimationUpdate(DynamicAnimation animation, float value, float velocity) {
                final int y = Math.round(value);
                // Not moving, or the touch operation is continuing
                if (mParams.y == y || mVelocityTracker != null) {
                    return;
                }
                // update y coordinate
                mParams.y = y;
                updateViewLayout();
            }
        });
        springAnimationY.start();
    }

    /**
     * Start fling animation(X coordinate)
     *
     * @param velocityX velocity X coordinate
     */
    private void startFlingAnimationX(float velocityX) {
        final FlingAnimation flingAnimationX = new FlingAnimation(new FloatValueHolder());
        flingAnimationX.setStartVelocity(velocityX);
        flingAnimationX.setMaxValue(mPositionLimitRect.right);
        flingAnimationX.setMinValue(mPositionLimitRect.left);
        flingAnimationX.setStartValue(mParams.x);
        flingAnimationX.setFriction(ANIMATION_FLING_X_FRICTION);
        flingAnimationX.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS);
        flingAnimationX.addUpdateListener(new DynamicAnimation.OnAnimationUpdateListener() {
            @Override
            public void onAnimationUpdate(DynamicAnimation animation, float value, float velocity) {
                final int x = Math.round(value);
                // Not moving, or the touch operation is continuing
                if (mParams.x == x || mVelocityTracker != null) {
                    return;
                }
                // update y coordinate
                mParams.x = x;
                updateViewLayout();
            }
        });
        flingAnimationX.start();
    }

    /**
     * Start fling animation(Y coordinate)
     *
     * @param velocityY velocity Y coordinate
     */
    private void startFlingAnimationY(float velocityY) {
        final FlingAnimation flingAnimationY = new FlingAnimation(new FloatValueHolder());
        flingAnimationY.setStartVelocity(velocityY);
        flingAnimationY.setMaxValue(mPositionLimitRect.bottom);
        flingAnimationY.setMinValue(mPositionLimitRect.top);
        flingAnimationY.setStartValue(mParams.y);
        flingAnimationY.setFriction(ANIMATION_FLING_Y_FRICTION);
        flingAnimationY.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS);
        flingAnimationY.addUpdateListener(new DynamicAnimation.OnAnimationUpdateListener() {
            @Override
            public void onAnimationUpdate(DynamicAnimation animation, float value, float velocity) {
                final int y = Math.round(value);
                // Not moving, or the touch operation is continuing
                if (mParams.y == y || mVelocityTracker != null) {
                    return;
                }
                // update y coordinate
                mParams.y = y;
                updateViewLayout();
            }
        });
        flingAnimationY.start();
    }

    /**
     * Check if it is attached to the Window and call WindowManager.updateLayout()
     */
    private void updateViewLayout() {
        if (!ViewCompat.isAttachedToWindow(this)) {
            return;
        }
        mWindowManager.updateViewLayout(this, mParams);
    }

    /**
     * Update animation initialization flag
     *
     * @param animation {@link ValueAnimator}
     */
    private void updateInitAnimation(ValueAnimator animation) {
        if (mAnimateInitialMove && animation.getDuration() <= animation.getCurrentPlayTime()) {
            mIsInitialAnimationRunning = false;
        }
    }

    /**
     * Get the final point of movement (X coordinate)
     *
     * @param startX Initial value of X coordinate
     * @param startY Initial value of Y coordinate
     * @return End point of X coordinate
     */
    private int getGoalPositionX(int startX, int startY) {
        int goalPositionX = startX;

        // Move to left or right edges
        if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_DEFAULT) {
            final boolean isMoveRightEdge = startX > (mMetrics.widthPixels - getWidth()) / 2;
            goalPositionX = isMoveRightEdge ? mPositionLimitRect.right : mPositionLimitRect.left;
        }
        // Move to left edges
        else if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_LEFT) {
            goalPositionX = mPositionLimitRect.left;
        }
        // Move to right edges
        else if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_RIGHT) {
            goalPositionX = mPositionLimitRect.right;
        }
        // Move to top/bottom/left/right edges
        else if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_NEAREST) {
            final int distLeftRight = Math.min(startX, mPositionLimitRect.width() - startX);
            final int distTopBottom = Math.min(startY, mPositionLimitRect.height() - startY);
            if (distLeftRight < distTopBottom) {
                final boolean isMoveRightEdge = startX > (mMetrics.widthPixels - getWidth()) / 2;
                goalPositionX = isMoveRightEdge ? mPositionLimitRect.right : mPositionLimitRect.left;
            }
        }
        // Move in the direction in which it is thrown
        else if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_THROWN) {
            if (mVelocityTracker != null && mVelocityTracker.getXVelocity() > mThrowMoveThreshold) {
                goalPositionX = mPositionLimitRect.right;
            } else if (mVelocityTracker != null && mVelocityTracker.getXVelocity() < -mThrowMoveThreshold) {
                goalPositionX = mPositionLimitRect.left;
            } else {
                final boolean isMoveRightEdge = startX > (mMetrics.widthPixels - getWidth()) / 2;
                goalPositionX = isMoveRightEdge ? mPositionLimitRect.right : mPositionLimitRect.left;
            }
        }

        return goalPositionX;
    }

    /**
     * Get the final point of movement (Y coordinate)
     *
     * @param startX Initial value of X coordinate
     * @param startY Initial value of Y coordinate
     * @return End point of Y coordinate
     */
    private int getGoalPositionY(int startX, int startY) {
        int goalPositionY = startY;

        // Move to top/bottom/left/right edges
        if (mMoveDirection == FloatingViewManager.MOVE_DIRECTION_NEAREST) {
            final int distLeftRight = Math.min(startX, mPositionLimitRect.width() - startX);
            final int distTopBottom = Math.min(startY, mPositionLimitRect.height() - startY);
            if (distLeftRight >= distTopBottom) {
                final boolean isMoveTopEdge = startY < (mMetrics.heightPixels - getHeight()) / 2;
                goalPositionY = isMoveTopEdge ? mPositionLimitRect.top : mPositionLimitRect.bottom;
            }
        }

        return goalPositionY;
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
        mMoveDirection = moveDirection;
    }

    /**
     * Use dynamic physics-based animations or not
     * Warning: Can not be used before API 16
     *
     * @param usePhysics Setting this to false will revert to using a ValueAnimator (default is true)
     */
    void usePhysics(boolean usePhysics) {
        mUsePhysics = usePhysics && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
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
     * 初期表示時にアニメーションするフラグを設定します。
     *
     * @param animateInitialMove 初期表示時にアニメーションする場合はtrue
     */
    void setAnimateInitialMove(boolean animateInitialMove) {
        mAnimateInitialMove = animateInitialMove;
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
        return (int) (mScreenTouchX - mLocalTouchX - mTouchXOffset);
    }

    /**
     * タッチ座標から算出されたFloatingViewのY座標
     *
     * @return FloatingViewのY座標
     */
    private int getYByTouch() {
        return (int) (mMetrics.heightPixels + mNavigationBarVerticalOffset - (mScreenTouchY - mLocalTouchY + getHeight() - mTouchYOffset));
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
        mIsMoveAccept = false;
        setVisibility(View.GONE);
    }

    int getState() {
        return mAnimationHandler.getState();
    }

    /**
     * Set the cutout's safe inset area
     *
     * @param safeInsetRect {@link FloatingViewManager#setSafeInsetRect(Rect)}
     */
    void setSafeInsetRect(Rect safeInsetRect) {
        mSafeInsetRect.set(safeInsetRect);
    }

    /**
     * アニメーションの制御を行うハンドラです。
     */
    static class FloatingAnimationHandler extends Handler {

        /**
         * アニメーションをリフレッシュするミリ秒
         */
        private static final long ANIMATION_REFRESH_TIME_MILLIS = 10L;

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
                floatingView.updateViewLayout();
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
                floatingView.updateViewLayout();
                sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS);
            }

        }

        /**
         * アニメーション時間から求められる位置を計算します。
         *
         * @param timeRate 時間比率
         * @return ベースとなる係数(0.0から1.0 ＋ α)
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
        void setState(@AnimationState int newState) {
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
        private final WeakReference<FloatingView> mFloatingView;

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
