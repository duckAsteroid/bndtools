package bndtools.internal.ui;

import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

import aQute.bnd.build.Project;
import aQute.libg.header.Attrs;
import bndtools.editor.model.BndEditModel;
import bndtools.model.clauses.ExportedPackage;
import bndtools.utils.FileUtils;

public class ToggleExportPackageAction implements IObjectActionDelegate {

    private ISelection selection;

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
     */
    public void run(IAction action) {
        if (selection instanceof IStructuredSelection) {
            for (Iterator<?> it = ((IStructuredSelection) selection).iterator(); it.hasNext();) {
                Object element = it.next();
                IPackageFragment pkg = null;
                if (element instanceof IProject) {
                    pkg = (IPackageFragment) element;
                } else if (element instanceof IAdaptable) {
                    pkg = (IPackageFragment) ((IAdaptable) element).getAdapter(IPackageFragment.class);
                }
                if (pkg != null) {
                    togglePackageExport(pkg);
                }
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action
     * .IAction, org.eclipse.jface.viewers.ISelection)
     */
    public void selectionChanged(IAction action, ISelection selection) {
        this.selection = selection;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.eclipse.ui.IObjectActionDelegate#setActivePart(org.eclipse.jface.
     * action.IAction, org.eclipse.ui.IWorkbenchPart)
     */
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
    }

    /**
     * Toggles the export of a package
     * 
     * @param pkg
     *            The java package to toggle
     */
    private void togglePackageExport(IPackageFragment pkg) {
        try {
            IJavaProject javaProject = pkg.getJavaProject();
            IProject project = javaProject.getProject();

            // Load project file and model
            IFile projectFile = project.getFile(Project.BNDFILE);
            BndEditModel projectModel;
            IDocument projectDocument = FileUtils.readFully(projectFile);
            if (projectDocument == null)
                projectDocument = new Document();
            projectModel = new BndEditModel();
            projectModel.loadFrom(projectDocument);

            List<ExportedPackage> exportedPackages = projectModel.getExportedPackages();
            ExportedPackage export = getExportedPackage(pkg, exportedPackages);
            if (export != null) {
                // remove from exports
                exportedPackages.remove(export);
                projectModel.setExportedPackages(exportedPackages);
            } else {
                // add new export
                Attrs attrs = new Attrs();
                projectModel.addExportedPackage(new ExportedPackage(pkg.getElementName(), attrs));
            }
            projectModel.saveChangesTo(projectDocument);
            FileUtils.writeFully(projectDocument, projectFile, true);
            // refresh decorator UI
            //ExportedPackageDecorator decorator = ExportedPackageDecorator.getPackageDecorator();
            //if (decorator != null) {
            //    decorator.refresh(pkg);
            //}

        } catch (Exception e) {
            e.printStackTrace();
        }
    }    

    /**
     * Find the exported package (given a java package and a list of exports)
     * 
     * @param pkg
     *            The package to find if exported
     * @param exports
     *            The current list of exports
     * @return the exported package, if exported - otherwise <code>null</code>
     */
    private ExportedPackage getExportedPackage(IPackageFragment pkg, List<ExportedPackage> exportedPackages) {
        if (exportedPackages == null)
            return null;
        for (ExportedPackage export : exportedPackages) {
            if (export.getName().equals(pkg.getElementName())) {
                return export;
            }
        }
        return null;
    }

}
