/*******************************************************************************
 * Copyright (c) 2012 Per Kr. Soreide.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Per Kr. Soreide - initial API and implementation
 *******************************************************************************/
package bndtools.release;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.Constants;

import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.bnd.service.RepositoryPlugin;
import aQute.lib.osgi.Builder;
import bndtools.diff.JarDiff;
import bndtools.release.api.ReleaseUtils;
import bndtools.release.nl.Messages;

public class WorkspaceAnalyserJob extends Job {

	protected final Shell shell;

	public WorkspaceAnalyserJob() {
		super(Messages.workspaceReleaseJob);
		this.shell = PlatformUI.getWorkbench().getDisplay().getActiveShell();
		setUser(true);
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {

		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}

		try {
			Collection<Project> projects = Activator.getWorkspace()
					.getAllProjects();

			monitor.beginTask(Messages.workspaceReleaseJob, projects.size() * 2);

			List<Project> orderedProjects = getBuildOrder(monitor,
					Activator.getWorkspace());
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}

			final List<ProjectDiff> projectDiffs = new ArrayList<ProjectDiff>();
			monitor.setTaskName("Processing Projects...");
			for (Project project : orderedProjects) {
				IProject eProject = ReleaseUtils.getProject(project);
				if (!eProject.isOpen() || !eProject.isAccessible()) {
					continue;
				}
				List<Builder> builders = project.getBuilder(null)
						.getSubBuilders();
				List<JarDiff> jarDiffs = null;
				for (Builder b : builders) {
					monitor.subTask("Processing " + b.getBsn() + "...");

					RepositoryPlugin baselineRepository = ReleaseHelper
							.getBaselineRepository(project, b.getBsn(),
									b.getProperty(Constants.BUNDLE_VERSION));
					JarDiff jarDiff = JarDiff.createJarDiff(project,
							baselineRepository, b.getBsn());
					if (jarDiff != null) {
						if (jarDiffs == null) {
							jarDiffs = new ArrayList<JarDiff>();
						}
						if (jarDiff.getChangedExportedPackages().size() > 0
								|| jarDiff.getChangedImportedPackages().size() > 0 || jarDiff.getChangedPrivatePackages().size() > 0) {
							jarDiffs.add(jarDiff);
						}
					}
				}
				if (jarDiffs != null && jarDiffs.size() > 0) {
					projectDiffs.add(new ProjectDiff(project, jarDiffs));
				}
				if (monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				monitor.worked(1);
			}

			if (projectDiffs.size() == 0) {
				Runnable runnable = new Runnable() {
					public void run() {
						MessageDialog.openInformation(shell, "Release Workspace Bundles", "No bundles requires release.");
					}
				};
				if (Display.getCurrent() == null) {
					Display.getDefault().syncExec(runnable);
				} else {
					runnable.run();
				}
				return Status.OK_STATUS;
			}

			ReleaseHelper.initializeProjectDiffs(projectDiffs);

			Runnable runnable = new Runnable() {
				public void run() {
					WorkspaceReleaseDialog dialog = new WorkspaceReleaseDialog(
							shell, projectDiffs);
					int ret = dialog.open();
					if (ret == WorkspaceReleaseDialog.OK) {
						WorkspaceReleaseJob releaseJob = new WorkspaceReleaseJob(
								projectDiffs, false);
						releaseJob.setRule(ResourcesPlugin.getWorkspace().getRoot());
						releaseJob.schedule();
					}
				}
			};

			if (Display.getCurrent() == null) {
				Display.getDefault().asyncExec(runnable);
			} else {
				runnable.run();
			}

		} catch (Exception e) {
			return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
					e.getMessage(), e);
		}
		return Status.OK_STATUS;
	}

	private static List<Project> getBuildOrder(IProgressMonitor monitor,
			Workspace workspace) throws Exception {

		List<Project> outlist = new ArrayList<Project>();
		monitor.setTaskName(Messages.calculatingBuildPath);
		for (Project project : workspace.getAllProjects()) {

			monitor.subTask("Resolving dependencies for " + project.getName());
			Collection<Project> dependsOn = project.getDependson();

			getBuildOrder(dependsOn, outlist);

			if (!outlist.contains(project)) {
				outlist.add(project);
			}
			monitor.worked(1);
		}
		return outlist;
	}

	private static void getBuildOrder(Collection<Project> dependsOn,
			List<Project> outlist) throws Exception {

		for (Project project : dependsOn) {
			if (!outlist.contains(project)) {
				Collection<Project> subProjects = project.getDependson();
				for (Project subProject : subProjects) {
					if (!outlist.contains(subProject)) {
						outlist.add(subProject);
					}
				}
			}
		}
	}
}
