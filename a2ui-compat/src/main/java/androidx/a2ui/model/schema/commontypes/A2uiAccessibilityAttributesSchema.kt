/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.a2ui.model.schema.commontypes

import androidx.a2ui.model.schema.A2uiCompositeSchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.commontypes.internal.SCHEMA_ID_COMMON_TYPES

/**
 * Holds accessibility properties for assistive technologies.
 *
 * @property description semantic description of the schema
 */
public class A2uiAccessibilityAttributesSchema(public override val description: String? = null) :
    A2uiCompositeSchema() {
    override val definitionName: String = "AccessibilityAttributes"
    override val schemaId: String = SCHEMA_ID_COMMON_TYPES

    public override fun getDefinition(): A2uiSchema =
        A2uiObjectSchema(
            properties =
                mapOf(
                    "label" to
                        A2uiDynamicStringSchema(
                            "A short string, typically 1 to 3 words, used by assistive technologies to convey the purpose or intent of an element. For example, an input field might have an accessible label of 'User ID' or a button might be labeled 'Submit'."
                        ),
                    "description" to
                        A2uiDynamicStringSchema(
                            "Additional information provided by assistive technologies about an element such as instructions, format requirements, or result of an action. For example, a mute button might have a label of 'Mute' and a description of 'Silences notifications about this conversation'."
                        ),
                ),
            description =
                "Attributes to enhance accessibility when using assistive technologies like screen readers.",
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiAccessibilityAttributesSchema) return false
        return description == other.description
    }

    override fun hashCode(): Int {
        return description?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "A2uiAccessibilityAttributesSchema(description=$description)"
    }

    public companion object {
        @JvmField
        public val DEFAULT_INSTANCE: A2uiAccessibilityAttributesSchema =
            A2uiAccessibilityAttributesSchema()
    }
}
