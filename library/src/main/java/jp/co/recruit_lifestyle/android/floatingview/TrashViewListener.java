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

/**
 * TrashViewのイベントを扱うリスナです。
 * INFO:削除アイコンが追従する仕様のため、OPENアニメーションのアニメーション終了は通知されません。
 */
interface TrashViewListener {

    /**
     * アニメーションを開始した時に通知されます。
     *
     * @param animationCode アニメーションコード
     */
    void onTrashAnimationStarted(int animationCode);

    /**
     * アニメーションが終了した時に通知されます。
     *
     * @param animationCode アニメーションコード
     */
    void onTrashAnimationEnd(int animationCode);


}
