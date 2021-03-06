/*******************************************************************************
 * Copyright (c) 2010 Neil Bartlett.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Neil Bartlett - initial API and implementation
 *******************************************************************************/
package bndtools.editor.pkgpatterns;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeSet;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;

import bndtools.Plugin;
import bndtools.javamodel.IJavaSearchContext;
import bndtools.utils.CachingContentProposalProvider;

public class PkgPatternsProposalProvider extends CachingContentProposalProvider {

	private final IJavaSearchContext searchContext;

	public PkgPatternsProposalProvider(IJavaSearchContext searchContext) {
		this.searchContext = searchContext;
	}

	@Override
	protected Collection<? extends IContentProposal> doGenerateProposals(String contents, int position) {
		String prefix = contents.substring(0, position);

		final int replaceFromPos;
		if(prefix.startsWith("!")) { //$NON-NLS-1$
			prefix = prefix.substring(1);
			replaceFromPos = 1;
		} else {
			replaceFromPos = 0;
		}

		Comparator<PkgPatternProposal> comparator = new Comparator<PkgPatternProposal>() {
			public int compare(PkgPatternProposal o1, PkgPatternProposal o2) {
				int result = o1.getPackageFragment().getElementName().compareTo(o2.getPackageFragment().getElementName());
				if(result == 0) {
					result = Boolean.valueOf(o1.isWildcard()).compareTo(Boolean.valueOf(o2.isWildcard()));
				}
				return result;
			}
		};
		final TreeSet<PkgPatternProposal> result = new TreeSet<PkgPatternProposal>(comparator);

		final IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { searchContext.getJavaProject() });
		final SearchPattern pattern = SearchPattern.createPattern("*" + prefix + "*", IJavaSearchConstants.PACKAGE, IJavaSearchConstants.DECLARATIONS, SearchPattern.R_PATTERN_MATCH);
		final SearchRequestor requestor = new SearchRequestor() {
			@Override
			public void acceptSearchMatch(SearchMatch match) throws CoreException {
				IPackageFragment pkg = (IPackageFragment) match.getElement();
				// Reject the default package and any package starting with "java." since these cannot be imported
				if(pkg.isDefaultPackage() || pkg.getElementName().startsWith("java."))
					return;

				result.add(new PkgPatternProposal(pkg, false, replaceFromPos));
				result.add(new PkgPatternProposal(pkg, true, replaceFromPos));
			}
		};
		IRunnableWithProgress runnable = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				try {
					new SearchEngine().search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, scope, requestor, monitor);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				}
			}
		};

		try {
			IRunnableContext runContext = searchContext.getRunContext();
			if(runContext != null) {
				runContext.run(false, false, runnable);
			} else {
				runnable.run(new NullProgressMonitor());
			}
			return result;
		} catch (InvocationTargetException e) {
			Plugin.log(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error searching for packages.", e));
			return Collections.emptyList();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Collections.emptyList();
		}
	}

	@Override
	protected boolean match(String contents, int position, IContentProposal proposal) {
		final String prefix = contents.substring(0, position);
		return ((PkgPatternProposal) proposal).getPackageFragment().getElementName().toLowerCase().indexOf(prefix.toLowerCase()) > -1;
	}

}
