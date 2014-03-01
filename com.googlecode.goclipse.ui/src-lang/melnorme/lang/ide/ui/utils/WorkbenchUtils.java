/*******************************************************************************
 * Copyright (c) 2011, 2014 Bruno Medeiros and other Contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Bruno Medeiros - initial API and implementation
 *******************************************************************************/
package melnorme.lang.ide.ui.utils;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.ResourceUtil;

public class WorkbenchUtils {
	
	/** Gets the active workbench window. */
	public static IWorkbenchWindow getActiveWorkbenchWindow() {
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow();
	}
	
	/** Gets the active workbench shell. */
	public static Shell getActiveWorkbenchShell() {
		IWorkbenchWindow window = getActiveWorkbenchWindow();
		if(window != null) {
			return window.getShell();
		}
		return null;
	}
	
	/** Gets the active workbench page. */
	public static IWorkbenchPage getActivePage() {
		IWorkbenchWindow window = getActiveWorkbenchWindow();
		if(window == null)
			return null;
		return window.getActivePage();
	}

	/**
	 * Attempts to guess the most relevant resource for the current workbench state
	 */
	public static IResource getContextResource() {
		IWorkbenchPage page = getActivePage();
		if (page == null) {
			return null;
		}
		
		final ISelection selection = page.getSelection();
		if (selection instanceof IStructuredSelection) {
			final IStructuredSelection ss = (IStructuredSelection) selection;
			if (!ss.isEmpty()) {
				final Object obj = ss.getFirstElement();
				if (obj instanceof IResource) {
					return (IResource) obj;
				}
			}
		}
		IEditorPart editor = page.getActiveEditor();
		if (editor == null) {
			return null;
		}
		
		IEditorInput editorInput = editor.getEditorInput();
		return ResourceUtil.getResource(editorInput);
	}
	
}