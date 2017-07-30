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

package com.facebook.buck.cxx;

import com.facebook.buck.cxx.toolchain.HeaderSymlinkTree;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.ExplicitBuildTargetSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.StringTemplateStep;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.io.Resources;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public final class HeaderSymlinkTreeWithModuleMap extends HeaderSymlinkTree {

  private static final Logger LOG = Logger.get(HeaderSymlinkTreeWithModuleMap.class);

  private static final Path MODULEMAP_TEMPLATE_PATH = Paths.get(
      Resources.getResource(
          HeaderSymlinkTreeWithModuleMap.class, "modulemap.st").getPath());

  @AddToRuleKey(stringify = true)
  private final Path moduleMapPath;

  @AddToRuleKey
  private final String moduleName;

  private HeaderSymlinkTreeWithModuleMap(
      BuildTarget target,
      ProjectFilesystem filesystem,
      Path root,
      ImmutableMap<Path, SourcePath> links,
      String moduleName) {
    super(target, filesystem, root, links);
    this.moduleName = moduleName;
    this.moduleMapPath = getPath(filesystem, target, moduleName);
  }

  public static HeaderSymlinkTreeWithModuleMap create(
      BuildTarget target,
      ProjectFilesystem filesystem,
      Path root,
      ImmutableMap<Path, SourcePath> links) {
    String moduleName = getModuleName(links);
    return new HeaderSymlinkTreeWithModuleMap(target, filesystem, root, links, moduleName);
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return new ExplicitBuildTargetSourcePath(getBuildTarget(), moduleMapPath);
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    LOG.debug("Generating post-build steps to write modulemap to %s", moduleMapPath);
    ImmutableSortedSet<Path> paths = getLinks().keySet();
    ImmutableList.Builder<Step> builder =
        ImmutableList.<Step>builder()
            .addAll(super.getBuildSteps(context, buildableContext))
            .add(new StringTemplateStep(
                MODULEMAP_TEMPLATE_PATH,
                getProjectFilesystem(),
                moduleMapPath,
                ImmutableMap.of(
                    "module_name", moduleName,
                    "has_umbrella_header", paths.contains(Paths.get(moduleName, moduleName + ".h")),
                    "has_swift_header", paths.contains(Paths.get(moduleName, moduleName + "-Swift.h")))
                ));

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

  static String getModuleName(ImmutableMap<Path, SourcePath> links) {
    return links.keySet().iterator().next().getName(0).toString();
  }

  @VisibleForTesting
  static Path getPath(ProjectFilesystem filesystem, BuildTarget target, String moduleName) {
    return BuildTargets.getGenPath(filesystem, target, "%s/" + moduleName + "/module.modulemap");
  }
}
