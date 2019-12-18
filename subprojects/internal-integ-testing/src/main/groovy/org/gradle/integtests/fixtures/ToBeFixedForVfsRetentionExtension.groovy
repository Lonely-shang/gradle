/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

class ToBeFixedForVfsRetentionExtension extends AbstractAnnotationDrivenExtension<ToBeFixedForVfsRetention> {
    @Override
    void visitSpec(SpecInfo spec) {
        if (GradleContextualExecuter.isVfsRetention()) {
            spec.allFeatures.each { feature ->
                def annotation = feature.featureMethod.reflection.getAnnotation(ToBeFixedForVfsRetention)
                if (annotation != null) {
                    spec.addListener(new CatchFeatureFailuresRunListener("vfs retention", feature))
                }
            }
        }
    }

    @Override
    void visitFeatureAnnotation(ToBeFixedForVfsRetention annotation, FeatureInfo feature) {
        // This override is required to satisfy spock's zealous runtime checks
    }
}
