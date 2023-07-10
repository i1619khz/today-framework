/*
 * Original Author -> Harry Yang (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © Harry Yang & 2017 - 2023 All Rights Reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see [http://www.gnu.org/licenses/]
 */

package cn.taketoday.gradle.plugin;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.distribution.Distribution;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator;
import org.gradle.jvm.application.tasks.CreateStartScripts;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.concurrent.Callable;

import cn.taketoday.gradle.tasks.run.InfraRun;

/**
 * Action that is executed in response to the {@link ApplicationPlugin} being applied.
 *
 * @author Andy Wilkinson
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0
 */
final class ApplicationPluginAction implements PluginApplicationAction {

  @Override
  public void execute(Project project) {
    JavaApplication javaApplication = project.getExtensions().getByType(JavaApplication.class);
    DistributionContainer distributions = project.getExtensions().getByType(DistributionContainer.class);
    Distribution distribution = distributions.create("infra");
    distribution.getDistributionBaseName()
            .convention((project.provider(() -> javaApplication.getApplicationName() + "-infra")));
    TaskProvider<CreateStartScripts> infraStartScripts = project.getTasks()
            .register("infraStartScripts", CreateStartScripts.class,
                    (task) -> configureCreateStartScripts(project, javaApplication, distribution, task));
    CopySpec binCopySpec = project.copySpec().into("bin").from(infraStartScripts);
    binCopySpec.setFileMode(0755);
    distribution.getContents().with(binCopySpec);
    applyApplicationDefaultJvmArgsToRunTasks(project.getTasks(), javaApplication);
  }

  private void applyApplicationDefaultJvmArgsToRunTasks(TaskContainer tasks, JavaApplication javaApplication) {
    applyApplicationDefaultJvmArgsToRunTask(tasks, javaApplication, InfraApplicationPlugin.INFRA_RUN_TASK_NAME);
    applyApplicationDefaultJvmArgsToRunTask(tasks, javaApplication, InfraApplicationPlugin.INFRA_TEST_RUN_TASK_NAME);
  }

  private void applyApplicationDefaultJvmArgsToRunTask(TaskContainer tasks, JavaApplication javaApplication,
          String taskName) {
    tasks.named(taskName, InfraRun.class)
            .configure((infraRun) -> infraRun.getConventionMapping()
                    .map("jvmArgs", javaApplication::getApplicationDefaultJvmArgs));
  }

  private void configureCreateStartScripts(Project project, JavaApplication javaApplication,
          Distribution distribution, CreateStartScripts createStartScripts) {
    createStartScripts
            .setDescription("Generates OS-specific start scripts to run the project as a Infra application.");
    ((TemplateBasedScriptGenerator) createStartScripts.getUnixStartScriptGenerator())
            .setTemplate(project.getResources().getText().fromString(loadResource("/unixStartScript.txt")));
    ((TemplateBasedScriptGenerator) createStartScripts.getWindowsStartScriptGenerator())
            .setTemplate(project.getResources().getText().fromString(loadResource("/windowsStartScript.txt")));
    project.getConfigurations().all((configuration) -> {
      if ("infraArchives".equals(configuration.getName())) {
        distribution.getContents().with(artifactFilesToLibCopySpec(project, configuration));
        createStartScripts.setClasspath(configuration.getArtifacts().getFiles());
      }
    });
    createStartScripts.getConventionMapping()
            .map("outputDir", () -> new File(project.getBuildDir(), "infraScripts"));
    createStartScripts.getConventionMapping().map("applicationName", javaApplication::getApplicationName);
    createStartScripts.getConventionMapping().map("defaultJvmOpts", javaApplication::getApplicationDefaultJvmArgs);
  }

  private CopySpec artifactFilesToLibCopySpec(Project project, Configuration configuration) {
    CopySpec copySpec = project.copySpec().into("lib").from(artifactFiles(configuration));
    copySpec.setFileMode(0644);
    return copySpec;
  }

  private Callable<FileCollection> artifactFiles(Configuration configuration) {
    return () -> configuration.getArtifacts().getFiles();
  }

  @Override
  public Class<? extends Plugin<Project>> getPluginClass() {
    return ApplicationPlugin.class;
  }

  private String loadResource(String name) {
    try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream(name))) {
      char[] buffer = new char[4096];
      int read;
      StringWriter writer = new StringWriter();
      while ((read = reader.read(buffer)) > 0) {
        writer.write(buffer, 0, read);
      }
      return writer.toString();
    }
    catch (IOException ex) {
      throw new GradleException("Failed to read '" + name + "'", ex);
    }
  }

}
