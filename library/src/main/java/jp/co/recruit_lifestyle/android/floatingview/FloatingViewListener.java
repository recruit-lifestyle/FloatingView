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


import android.view.View;

/**
 * FloatingViewのリスナです。
 */
public interface FloatingViewListener {

    /**
     * FloatingViewを終了する際に呼び出されます。
     */
    void onFinishFloatingView();

    /**
     * Callback when touch action finished.
     *
     * @param isFinishing Whether FloatingView is being deleted or not.
     * @param x           x coordinate
     * @param y           y coordinate
     */
    void onTouchFinished(View v,boolean isFinishing, int x, int y);

}
