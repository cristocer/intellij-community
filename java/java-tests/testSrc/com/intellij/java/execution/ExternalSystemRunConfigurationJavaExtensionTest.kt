// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.FakeConfigurationFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.idea.Bombed
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.JavaProjectTestCase
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.runInEdtAndWait
import org.apache.log4j.Logger
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import java.util.*

@Bombed(month = Calendar.APRIL, day = 20, user = "Kirill Timofeev")
class ExternalSystemRunConfigurationJavaExtensionTest : JavaProjectTestCase() {

  fun `test ExecutionException thrown from RunConfigurationExtension#updateJavaParameters should terminate execution`() {
    ExtensionTestUtil.maskExtensions(RunConfigurationExtension.EP_NAME, listOf(CantUpdateJavaParametersExtension()), testRootDisposable)
    val configuration = createExternalSystemRunConfiguration()
    val notificationsCollector = NotificationsCollector()
    Messages.setTestDialog(notificationsCollector)
    LoggedErrorProcessor.setNewInstance(object : LoggedErrorProcessor() {
      override fun processError(message: String?, t: Throwable?, details: Array<out String>?, logger: Logger) {
        // don't fail this if `LOG.error()` was called for our exception somewhere
        if (t is FakeExecutionException) return
        super.processError(message, t, details, logger)
      }
    })
    runInEdtAndWait {
      ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), configuration).buildAndExecute()
    }
    assertThat(assertOneElement(notificationsCollector.notifications), containsString(FakeExecutionException.MESSAGE))
  }

  fun `test only applicable configuration extensions should be processed`() {
    ExtensionTestUtil.maskExtensions(RunConfigurationExtension.EP_NAME, listOf(UnApplicableConfigurationExtension()), testRootDisposable)
    val configuration = createExternalSystemRunConfiguration()
    runInEdtAndWait {
      ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), configuration).buildAndExecute()
    }
  }

  private fun createExternalSystemRunConfiguration() =
    ExternalSystemRunConfiguration(ProjectSystemId("FakeExternalSystem"), project, FakeConfigurationFactory(), "FakeConfiguration").apply {
      settings.externalProjectPath = module.moduleFilePath // any string to prevent NPE
    }

  private companion object {

    class CantUpdateJavaParametersExtension : RunConfigurationExtension() {
      override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean = true
      override fun <T : RunConfigurationBase<*>?> updateJavaParameters(configuration: T,
                                                                       params: JavaParameters,
                                                                       runnerSettings: RunnerSettings?) {
        throw FakeExecutionException()
      }

      override fun attachToProcess(configuration: RunConfigurationBase<*>, handler: ProcessHandler, runnerSettings: RunnerSettings?) {
        // 'attachToProcess' is called after 'updateJavaParameters'
        fail("Should not be here")
      }
    }

    class UnApplicableConfigurationExtension : RunConfigurationExtension() {
      override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean = false
      override fun <T : RunConfigurationBase<*>?> updateJavaParameters(configuration: T,
                                                                       params: JavaParameters,
                                                                       runnerSettings: RunnerSettings?) {
        fail("Should not be here")
      }


      override fun attachToProcess(configuration: RunConfigurationBase<*>, handler: ProcessHandler, runnerSettings: RunnerSettings?) {
        fail("Should not be here")
      }
    }

    class FakeExecutionException : ExecutionException(MESSAGE) {
      companion object {
        const val MESSAGE = "Fake Execution Exception"
      }
    }

    class NotificationsCollector : TestDialog {
      val notifications = mutableListOf<String>()
      override fun show(message: String): Int {
        notifications += message
        return Messages.OK
      }
    }
  }
}