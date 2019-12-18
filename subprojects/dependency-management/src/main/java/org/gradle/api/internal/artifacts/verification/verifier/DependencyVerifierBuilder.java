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
package org.gradle.api.internal.artifacts.verification.verifier;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.Checksum;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.ImmutableArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.ImmutableComponentVerificationMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DependencyVerifierBuilder {
    private final Map<ModuleComponentIdentifier, ComponentVerificationsBuilder> byComponent = Maps.newLinkedHashMap();
    private final List<DependencyVerificationConfiguration.TrustedArtifact> trustedArtifacts = Lists.newArrayList();
    private final List<DependencyVerificationConfiguration.TrustedKey> trustedKeys = Lists.newArrayList();
    private final List<URI> keyServers = Lists.newArrayList();
    private final Set<String> ignoredKeys = Sets.newLinkedHashSet();
    private boolean isVerifyMetadata = true;
    private boolean isVerifySignatures = false;

    public void addChecksum(ModuleComponentArtifactIdentifier artifact, ChecksumKind kind, String value, @Nullable String origin) {
        ModuleComponentIdentifier componentIdentifier = artifact.getComponentIdentifier();
        byComponent.computeIfAbsent(componentIdentifier, id -> new ComponentVerificationsBuilder(id))
            .addChecksum(artifact, kind, value, origin);
    }

    public void addTrustedKey(ModuleComponentArtifactIdentifier artifact, String key) {
        ModuleComponentIdentifier componentIdentifier = artifact.getComponentIdentifier();
        byComponent.computeIfAbsent(componentIdentifier, id -> new ComponentVerificationsBuilder(id))
            .addTrustedKey(artifact, key);
    }

    public void setVerifyMetadata(boolean verifyMetadata) {
        isVerifyMetadata = verifyMetadata;
    }

    public boolean isVerifyMetadata() {
        return isVerifyMetadata;
    }

    public boolean isVerifySignatures() {
        return isVerifySignatures;
    }

    public void setVerifySignatures(boolean verifySignatures) {
        isVerifySignatures = verifySignatures;
    }

    public void addTrustedArtifact(@Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex) {
        validateUserInput(group, name, version, fileName);
        trustedArtifacts.add(new DependencyVerificationConfiguration.TrustedArtifact(group, name, version, fileName, regex));
    }

    public void addIgnoredKey(String keyId) {
        ignoredKeys.add(keyId);
    }

    public void addTrustedKey(String keyId, @Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex) {
        validateUserInput(group, name, version, fileName);
        trustedKeys.add(new DependencyVerificationConfiguration.TrustedKey(keyId, group, name, version, fileName, regex));
    }

    private void validateUserInput(@Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName) {
        // because this can be called from parsing XML, we need to perform additional verification
        if (group == null && name == null && version == null && fileName == null) {
            throw new InvalidUserDataException("A trusted artifact must have at least one of group, name, version or file name not null");
        }
    }

    public DependencyVerifier build() {
        ImmutableMap.Builder<ComponentIdentifier, ComponentVerificationMetadata> builder = ImmutableMap.builderWithExpectedSize(byComponent.size());
        for (Map.Entry<ModuleComponentIdentifier, ComponentVerificationsBuilder> entry : byComponent.entrySet()) {
            builder.put(entry.getKey(), entry.getValue().build());
        }
        return new DependencyVerifier(builder.build(), new DependencyVerificationConfiguration(isVerifyMetadata, isVerifySignatures, trustedArtifacts, ImmutableList.copyOf(keyServers), ImmutableSet.copyOf(ignoredKeys), trustedKeys));
    }

    public List<DependencyVerificationConfiguration.TrustedArtifact> getTrustedArtifacts() {
        return trustedArtifacts;
    }

    public void addKeyServer(URI uri) {
        keyServers.add(uri);
    }

    private static class ComponentVerificationsBuilder {
        private final ModuleComponentIdentifier component;
        private final Map<String, ArtifactVerificationBuilder> byArtifact = Maps.newLinkedHashMap();

        private ComponentVerificationsBuilder(ModuleComponentIdentifier component) {
            this.component = component;
        }

        void addChecksum(ModuleComponentArtifactIdentifier artifact, ChecksumKind kind, String value, @Nullable String origin) {
            byArtifact.computeIfAbsent(artifact.getFileName(), id -> new ArtifactVerificationBuilder()).addChecksum(kind, value, origin);
        }

        void addTrustedKey(ModuleComponentArtifactIdentifier artifact, String key) {
            byArtifact.computeIfAbsent(artifact.getFileName(), id -> new ArtifactVerificationBuilder()).addTrustedKey(key);
        }

        private static ArtifactVerificationMetadata toArtifactVerification(Map.Entry<String, ArtifactVerificationBuilder> entry) {
            return new ImmutableArtifactVerificationMetadata(entry.getKey(), entry.getValue().buildChecksums(), entry.getValue().buildPgpKeys());
        }

        ComponentVerificationMetadata build() {
            return new ImmutableComponentVerificationMetadata(component,
                byArtifact.entrySet()
                    .stream()
                    .map(ComponentVerificationsBuilder::toArtifactVerification)
                    .collect(Collectors.toList())
            );
        }
    }

    private static class ArtifactVerificationBuilder {
        private final Map<ChecksumKind, ChecksumBuilder> builder = Maps.newEnumMap(ChecksumKind.class);
        private final Set<String> pgpKeys = Sets.newLinkedHashSet();

        void addChecksum(ChecksumKind kind, String value, @Nullable String origin) {
            ChecksumBuilder builder = this.builder.computeIfAbsent(kind, ChecksumBuilder::new);
            builder.addChecksum(value);
            if (origin != null) {
                builder.withOrigin(origin);
            }
        }

        List<Checksum> buildChecksums() {
            return builder.values()
                .stream()
                .map(ChecksumBuilder::build)
                .collect(Collectors.toList());
        }

        public void addTrustedKey(String key) {
            pgpKeys.add(key);
        }

        public Set<String> buildPgpKeys() {
            return pgpKeys;
        }
    }

    private static class ChecksumBuilder {
        private final ChecksumKind kind;
        private String value;
        private String origin;
        private Set<String> alternatives;

        private ChecksumBuilder(ChecksumKind kind) {
            this.kind = kind;
        }

        /**
         * Sets the origin, if not set already. This is
         * mostly used for automatic generation of checksums
         */
        void withOrigin(String origin) {
            if (this.origin == null) {
                this.origin = origin;
            }
        }

        void addChecksum(String checksum) {
            if (value == null) {
                value = checksum;
            } else if (!value.equals(checksum)) {
                if (alternatives == null) {
                    alternatives = Sets.newLinkedHashSet();
                }
                alternatives.add(checksum);
            }
        }

        Checksum build() {
            return new Checksum(
                kind,
                value,
                alternatives,
                origin
            );
        }
    }
}
