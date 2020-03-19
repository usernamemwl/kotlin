/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("UnstableApiUsage")

package org.jetbrains.kotlin.idea.scripting.gradle

import com.intellij.notification.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.EditorFloatingToolbar
import com.intellij.openapi.externalSystem.autoimport.ProjectRefreshFloatingProvider
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.scripting.gradle.importing.KotlinDslScriptModelResolver
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.plugins.gradle.service.project.GradlePartialResolverPolicy
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

fun runPartialGradleImport(project: Project) {
    val gradleSettings = ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID)
    val projectSettings = gradleSettings.getLinkedProjectsSettings()
        .filterIsInstance<GradleProjectSettings>()
        .firstOrNull() ?: return

    ExternalSystemUtil.refreshProject(
        projectSettings.externalProjectPath,
        ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
            .projectResolverPolicy(
                GradlePartialResolverPolicy {
                    it is KotlinDslScriptModelResolver
                }
            )
    )
}

private var Project.notificationPanel: Notification?
        by UserDataProperty<Project, Notification>(Key.create("load.script.configuration.panel"))


fun showNotificationForProjectImport(project: Project, callback: () -> Unit) {
    runInEdt {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val toolbarProvider = ProjectRefreshFloatingProvider.getExtension()
        toolbarProvider.updateAllToolbarComponents()
    }
    if (project.notificationPanel != null) return

    val notification = getNotificationGroup().createNotification(
        "Script configurations may be changed. They affect code highlighting, completion and other IDE functionality for *.gradle.kts scripts",
        NotificationType.INFORMATION
    )
    notification.addAction(NotificationAction.createSimple("Import changes") {
        callback()
    })
    notification.addAction(NotificationAction.createSimple("Enable auto-reload") {
        callback()
        KotlinScriptingSettings.getInstance(project).isAutoReloadEnabled = true
    })
    project.notificationPanel = notification
    notification.notify(project)
}

fun hideNotificationForProjectImport(project: Project): Boolean {
    if (project.notificationPanel == null) return false
    project.notificationPanel?.expire()
    project.notificationPanel = null
    return true
}

private fun getNotificationGroup(): NotificationGroup {
    return NotificationGroup.findRegisteredGroup("Kotlin DSL script configurations")
        ?: NotificationGroup("Kotlin DSL script configurations", NotificationDisplayType.STICKY_BALLOON, true)
}

class ScriptConfigurationsRefreshFloatingProvider : AbstractFloatingToolbarProvider(ACTION_GROUP) {

    override val priority = 200

    override val autoHideable = false

    companion object {
        const val ACTION_GROUP = "ProjectRefreshActionGroup"

        fun getExtension(): ScriptConfigurationsRefreshFloatingProvider {
            return EditorFloatingToolbar.EP_NAME.findExtensionOrFail(ScriptConfigurationsRefreshFloatingProvider::class.java)
        }
    }
}