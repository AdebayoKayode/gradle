/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformException;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.DefaultAttributeContainer;
import org.gradle.internal.reflect.DirectInstantiator;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

public class ArtifactTransformRegistrations {
    private final List<ArtifactTransformRegistration> transforms = Lists.newArrayList();

    public void registerTransform(Class<? extends ArtifactTransform> type, Action<? super ArtifactTransform> config) {
        ArtifactTransform artifactTransform = DirectInstantiator.INSTANCE.newInstance(type);
        AttributeContainerInternal from = new DefaultAttributeContainer();

        DefaultArtifactTransformTargets registry = new DefaultArtifactTransformTargets();
        artifactTransform.configure(from, registry);

        for (AttributeContainerInternal to : registry.getNewTargets()) {
            ArtifactTransformRegistration registration = new ArtifactTransformRegistration(from.asImmutable(), to.asImmutable(), type, config);
            transforms.add(registration);
        }
    }

    public Iterable<ArtifactTransformRegistration> getTransforms() {
        return transforms;
    }

    public final class ArtifactTransformRegistration {
        public final AttributeContainer from;
        public final AttributeContainer to;
        public final Class<? extends ArtifactTransform> type;
        public final Action<? super ArtifactTransform> config;
        private Transformer<List<File>, File> transform;

        ArtifactTransformRegistration(AttributeContainer from, AttributeContainer to, Class<? extends ArtifactTransform> type, Action<? super ArtifactTransform> config) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.config = config;

            this.transform = createArtifactTransformer();
        }

        public Transformer<List<File>, File> getTransform() {
            return transform;
        }

        private Transformer<List<File>, File> createArtifactTransformer() {
            ArtifactTransform artifactTransform = DirectInstantiator.INSTANCE.newInstance(type);
            config.execute(artifactTransform);
            return new ArtifactFileTransformer(artifactTransform, to);
        }
    }

    private static class ArtifactFileTransformer implements Transformer<List<File>, File> {
        private final ArtifactTransform artifactTransform;
        private final AttributeContainer outputAttributes;

        private ArtifactFileTransformer(ArtifactTransform artifactTransform, AttributeContainer outputAttributes) {
            this.artifactTransform = artifactTransform;
            this.outputAttributes = outputAttributes;
        }

        @Override
        public List<File> transform(File input) {
            if (artifactTransform.getOutputDirectory() != null) {
                artifactTransform.getOutputDirectory().mkdirs();
            }
            List<File> outputs = doTransform(input);
            if (outputs == null) {
                throw new ArtifactTransformException(input, outputAttributes, artifactTransform, new NullPointerException("Illegal null output from ArtifactTransform"));
            }
            for (File output : outputs) {
                if (!output.exists()) {
                    throw new ArtifactTransformException(input, outputAttributes, artifactTransform, new FileNotFoundException("ArtifactTransform output '" + output.getPath() + "' does not exist"));
                }
            }
            return outputs;
        }

        private List<File> doTransform(File input) {
            try {
                return artifactTransform.transform(input, outputAttributes);
            } catch (Exception e) {
                throw new ArtifactTransformException(input, outputAttributes, artifactTransform, e);
            }
        }
    }
}
