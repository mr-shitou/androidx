/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.support.room.solver.query

import com.android.support.room.ext.L
import com.android.support.room.solver.CodeGenScope
import com.android.support.room.solver.types.TypeConverter

/**
 * Knows how to convert a query parameter into arguments
 */
class BasicQueryParameterAdapter(val converter : TypeConverter) : QueryParameterAdapter(false) {
    override fun getArgCount(inputVarName: String, outputVarName : String, scope: CodeGenScope) {
        throw UnsupportedOperationException("should not call getArgCount on basic adapters." +
                "It is always one.")
    }

    override fun convert(inputVarName: String, outputVarName: String, startIndexVarName: String,
                         scope: CodeGenScope) {
        converter.convertForward(inputVarName, "$outputVarName[$startIndexVarName]", scope)
    }
}
