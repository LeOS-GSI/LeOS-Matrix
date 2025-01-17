/*
 * Copyright (c) 2022 The Matrix.org Foundation C.I.C.
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

package org.matrix.android.sdk.internal.util.database

import io.realm.DynamicRealm
import io.realm.RealmObjectSchema
import timber.log.Timber

internal abstract class RealmMigrator(private val realm: DynamicRealm,
                                      private val targetSchemaVersion: Int) {
    open fun perform() {
        Timber.d("Migrate ${realm.configuration.realmFileName} to $targetSchemaVersion")
        doMigrate(realm)
    }

    abstract fun doMigrate(realm: DynamicRealm)

    protected fun RealmObjectSchema.addFieldIfNotExists(fieldName: String, fieldType: Class<*>): RealmObjectSchema {
        if (!hasField(fieldName)) {
            addField(fieldName, fieldType)
        }
        return this
    }

    protected fun RealmObjectSchema.removeFieldIfExists(fieldName: String): RealmObjectSchema {
        if (hasField(fieldName)) {
            removeField(fieldName)
        }
        return this
    }

    protected fun RealmObjectSchema.setRequiredIfNotAlready(fieldName: String, isRequired: Boolean): RealmObjectSchema {
        if (isRequired != isRequired(fieldName)) {
            setRequired(fieldName, isRequired)
        }
        return this
    }
}
