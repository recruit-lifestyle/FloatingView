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
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

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
     * FloatingViewと削除ボタンが重なった時のバイブレーション時間(ミリ秒)
     */
    private static final long VIBRATE_INTERSECTS_MILLIS = 15;

    /**
     * Viewの形が円形の場合
     */
    public static final float SHAPE_CIRCLE = 1.0f;

    /**
     * Viewの形が四角形の場合
     */
    public static final float SHAPE_RECTANGLE = 1.4142f;

    /**
     * Context
     */
    private final Context mContext;

    /**
     * WindowManager
     */
    private final WindowManager mWindowManager;

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
     * Vibrator
     */
    private final Vibrator mVibrator;

    /**
     * タッチの移動を許可するフラグ
     * 画面回転時にタッチ処理を受け付けないようにするためのフラグです
     */
    private boolean mIsMoveAccept;

    /**
     * 現在の表示モード
     */
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
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mFloatingViewListener = listener;
        mFloatingViewRect = new Rect();
        mTrashViewRect = new Rect();
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
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
        // INFO:TrashViewとFloatingViewは同じGravityにする必要があります
        mTrashView.getWindowDrawingRect(mTrashViewRect);
        mTargetFloatingView.getWindowDrawingRect(mFloatingViewRect);
        return Rect.intersects(mTrashViewRect, mFloatingViewRect);
    }

    /**
     * 画面がフルスクリーンになった場合はViewを非表示にします。
     */
    @Override
    public void onScreenChanged(boolean isFullscreen) {
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
                floatingView.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
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
     * FloatingViewのタッチをロックします。
     */
    @Override
    public void onTrashAnimationStarted(int animationCode) {
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
    public void onTrashAnimationEnd(int animationCode) {

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
                mVibrator.vibrate(VIBRATE_INTERSECTS_MILLIS);
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
    public void setFixedTrashIconImage(int resId) {
        mTrashView.setFixedTrashIconImage(resId);
    }

    /**
     * アクションする削除アイコンの画像を設定します。
     *
     * @param resId drawable ID
     */
    public void setActionTrashIconImage(int resId) {
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
     * @param displayMode DISPLAY_MODE_HIDE_ALWAYS or DISPLAY_MODE_HIDE_FULLSCREEN or DISPLAY_MODE_SHOW_ALWAYS
     */
    public void setDisplayMode(int displayMode) {
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
     * ViewをWindowに貼り付けます。
     *
     * @param view       フローティングさせるView
     * @param shape      フローティングさせるViewの矩形（SHAPE_RECTANGLE or SHAPE_CIRCLE）
     * @param overMargin マージン
     */
    public void addViewToWindow(View view, float shape, int overMargin) {
        final boolean isFirstAttach = mFloatingViewList.isEmpty();
        // FloatingView
        final FloatingView floatingView = new FloatingView(mContext);
        floatingView.addView(view);
        view.setClickable(false);
        floatingView.setOnTouchListener(this);
        floatingView.setShape(shape);
        floatingView.setOverMargin(overMargin);
        floatingView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                floatingView.getViewTreeObserver().removeOnPreDrawListener(this);
                mTrashView.calcActionTrashIconPadding(floatingView.getMeasuredWidth(), floatingView.getMeasuredHeight(), floatingView.getShape());
                return false;
            }
        });
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
    }

    /**
     * ViewをWindowから取り外します。
     *
     * @param floatingView FloatingView
     */
    private void removeViewToWindow(FloatingView floatingView) {
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

}
