/*
 * Copyright (c) 2021 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.core.pushers

import android.content.Context
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.core.time.Clock
import im.vector.app.core.receiver.AlarmSyncBroadcastReceiver
import im.vector.app.core.receiver.BackgroundSyncStarter
import im.vector.app.features.settings.VectorPreferences

object StateHelper {
    fun onEnterForeground(context: Context, activeSessionHolder: ActiveSessionHolder) {
        // try to stop all regardless of background mode
        activeSessionHolder.getSafeActiveSession()?.stopAnyBackgroundSync()
        AlarmSyncBroadcastReceiver.cancelAlarm(context)
    }

    fun onEnterBackground(context: Context, vectorPreferences: VectorPreferences, activeSessionHolder: ActiveSessionHolder, clock: Clock) {
        BackgroundSyncStarter.start(context, vectorPreferences, activeSessionHolder, clock)
    }
}
