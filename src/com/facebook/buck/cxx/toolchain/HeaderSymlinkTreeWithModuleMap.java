/*
 * Copyright 2015-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.cxx.toolchain;

import com.facebook.buck.apple.clang.ModuleMap;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.ExplicitBuildTargetSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.WriteFileStep;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public final class HeaderSymlinkTreeWithModuleMap extends HeaderSymlinkTree {

  private static final Logger LOG = Logger.get(HeaderSymlinkTreeWithModuleMap.class);

  @AddToRuleKey(stringify = true)
  private final Path moduleMapPath;

  @AddToRuleKey private final Optional<String> moduleName;

  private HeaderSymlinkTreeWithModuleMap(
      BuildTarget target,
      ProjectFilesystem filesystem,
      Path root,
      ImmutableMap<Path, SourcePath> links,
      Optional<String> moduleName) {
    super(target, filesystem, root, links);
    this.moduleName = moduleName;
    this.moduleMapPath = getPath(filesystem, target, moduleName.orElse(""));
  }

  public static HeaderSymlinkTreeWithModuleMap create(
      BuildTarget target,
      ProjectFilesystem filesystem,
      Path root,
      ImmutableMap<Path, SourcePath> links) {
    Optional<String> moduleName = getModuleName(links);
    return new HeaderSymlinkTreeWithModuleMap(target, filesystem, root, links, moduleName);
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return ExplicitBuildTargetSourcePath.of(getBuildTarget(), moduleMapPath);
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    LOG.debug("Generating post-build steps to write modulemap to %s", moduleMapPath);
    ImmutableSortedSet<Path> paths = getLinks().keySet();
    ImmutableList.Builder<Step> builder =
        ImmutableList.<Step>builder().addAll(super.getBuildSteps(context, buildableContext));
    if (moduleName.isPresent()) {
      builder.add(
          new WriteFileStep(
              getProjectFilesystem(),
              new ModuleMap(
                      moduleName.get(),
                      paths.contains(Paths.get(moduleName.get(), moduleName.get() + "-Swift.h"))
                          ? ModuleMap.SwiftMode.INCLUDE_SWIFT_HEADER
                          : ModuleMap.SwiftMode.NO_SWIFT)
                  .render(),
              moduleMapPath,
              false));
    }
    return builder.build();
  }

  @Override
  public Path getIncludePath() {
    return getRoot();
  }

  @Override
  public Optional<Path> getModuleMap() {
    return Optional.of(getProjectFilesystem().resolve(moduleMapPath));
  }

  static Optional<String> getModuleName(ImmutableMap<Path, SourcePath> links) {
    if (links.size() > 0) {
      return Optional.of(links.keySet().iterator().next().getName(0).toString());
    } else {
      return Optional.empty();
    }
  }

  @VisibleForTesting
  static Path getPath(ProjectFilesystem filesystem, BuildTarget target, String moduleName) {
    return BuildTargets.getGenPath(filesystem, target, "%s/" + moduleName + "/module.modulemap");
  }
}
