// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.sandbox;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Executor.ActionContext;
import com.google.devtools.build.lib.analysis.BlazeDirectories;
import com.google.devtools.build.lib.buildtool.BuildRequest;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.exec.ActionContextProvider;
import com.google.devtools.build.lib.exec.ExecutionOptions;
import com.google.devtools.build.lib.runtime.CommandEnvironment;
import com.google.devtools.build.lib.util.OS;
import com.google.devtools.build.lib.vfs.Path;
import java.io.IOException;

/**
 * Provides the sandboxed spawn strategy.
 */
final class SandboxActionContextProvider extends ActionContextProvider {

  private static final String SANDBOX_NOT_SUPPORTED_MESSAGE =
      "Sandboxed execution is not supported on your system and thus hermeticity of actions cannot "
          + "be guaranteed. See http://bazel.build/docs/bazel-user-manual.html#sandboxing for more "
          + "information. You can turn off this warning via --ignore_unsupported_sandboxing";

  @SuppressWarnings("unchecked")
  private final ImmutableList<ActionContext> contexts;

  private SandboxActionContextProvider(ImmutableList<ActionContext> contexts) {
    this.contexts = contexts;
  }

  public static SandboxActionContextProvider create(
      CommandEnvironment env, BuildRequest buildRequest, Path sandboxBase) throws IOException {
    ImmutableList.Builder<ActionContext> contexts = ImmutableList.builder();

    BlazeDirectories blazeDirs = env.getDirectories();
    boolean verboseFailures = buildRequest.getOptions(ExecutionOptions.class).verboseFailures;
    String productName = env.getRuntime().getProductName();

    switch (OS.getCurrent()) {
      case LINUX:
        if (LinuxSandboxedStrategy.isSupported(env)) {
          contexts.add(
              new LinuxSandboxedStrategy(
                  buildRequest, env.getDirectories(), sandboxBase, verboseFailures));
        } else {
          if (!buildRequest.getOptions(SandboxOptions.class).ignoreUnsupportedSandboxing) {
            env.getReporter().handle(Event.warn(SANDBOX_NOT_SUPPORTED_MESSAGE));
          }
          contexts.add(
              new ProcessWrapperSandboxedStrategy(
                  buildRequest, env.getDirectories(), sandboxBase, verboseFailures));
        }
        break;
      case DARWIN:
        if (DarwinSandboxRunner.isSupported()) {
          contexts.add(
              DarwinSandboxedStrategy.create(
                  buildRequest,
                  env.getClientEnv(),
                  blazeDirs,
                  sandboxBase,
                  verboseFailures,
                  productName));
        } else {
          if (!buildRequest.getOptions(SandboxOptions.class).ignoreUnsupportedSandboxing) {
            env.getReporter().handle(Event.warn(SANDBOX_NOT_SUPPORTED_MESSAGE));
          }
        }
        break;
      case FREEBSD:
        contexts.add(
            new ProcessWrapperSandboxedStrategy(
                buildRequest, env.getDirectories(), sandboxBase, verboseFailures));
        break;
      default:
        // No sandboxing available.
    }

    return new SandboxActionContextProvider(contexts.build());
  }

  @Override
  public Iterable<ActionContext> getActionContexts() {
    return contexts;
  }
}
