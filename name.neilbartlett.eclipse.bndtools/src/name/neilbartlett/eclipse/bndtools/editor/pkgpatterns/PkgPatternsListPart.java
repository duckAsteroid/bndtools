package name.neilbartlett.eclipse.bndtools.editor.pkgpatterns;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import name.neilbartlett.eclipse.bndtools.Plugin;
import name.neilbartlett.eclipse.bndtools.editor.model.BndEditModel;
import name.neilbartlett.eclipse.bndtools.editor.model.HeaderClause;
import name.neilbartlett.eclipse.bndtools.utils.CollectionUtils;
import name.neilbartlett.eclipse.bndtools.utils.EditorUtils;
import name.neilbartlett.eclipse.bndtools.views.impexp.AnalyseImportsJob;
import name.neilbartlett.eclipse.bndtools.views.impexp.ImportsExportsView;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.SectionPart;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.forms.editor.IFormPage;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ImageHyperlink;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.ide.ResourceUtil;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Constants;

public abstract class PkgPatternsListPart extends SectionPart implements PropertyChangeListener {

	private final String propertyName;
	
	protected List<HeaderClause> clauses;
	
	private IManagedForm managedForm;
	private TableViewer viewer;
	private BndEditModel model;
	
	
	private final Image imgAnalyse = AbstractUIPlugin.imageDescriptorFromPlugin(Plugin.PLUGIN_ID, "/icons/cog_go.png").createImage();
	
	public PkgPatternsListPart(Composite parent, FormToolkit toolkit, int style, String propertyName) {
		super(parent, toolkit, style);
		this.propertyName = propertyName;
		createSection(getSection(), toolkit);
	}
	
	void createSection(Section section, FormToolkit toolkit) {
		section.setText("Import Package Patterns");
		
		Composite composite = toolkit.createComposite(section);
		section.setClient(composite);
		
		Table table = toolkit.createTable(composite, SWT.MULTI | SWT.FULL_SELECTION);
		viewer = new TableViewer(table);
		viewer.setContentProvider(new ArrayContentProvider());
		viewer.setLabelProvider(new PkgPatternsLabelProvider());
		
		final Button btnAdd = toolkit.createButton(composite, "Add...", SWT.PUSH);
		final Button btnInsert = toolkit.createButton(composite, "Insert", SWT.PUSH);
		final Button btnRemove = toolkit.createButton(composite, "Remove", SWT.PUSH);
		final Button btnMoveUp = toolkit.createButton(composite, "Up", SWT.PUSH);
		final Button btnMoveDown = toolkit.createButton(composite, "Down", SWT.PUSH);
		toolkit.createLabel(composite, ""); // Spacer
		
		ImageHyperlink lnkAnalyse = toolkit.createImageHyperlink(composite, SWT.LEFT);
		lnkAnalyse.setText("Analyse Packages");
		lnkAnalyse.setImage(imgAnalyse);

		
		// Listeners
		lnkAnalyse.addHyperlinkListener(new HyperlinkAdapter() {
			@Override
			public void linkActivated(HyperlinkEvent event) {
				IFormPage formPage = (IFormPage) getManagedForm().getContainer();
				IFile file = ResourceUtil.getFile(formPage.getEditorInput());
				final IWorkbenchPage workbenchPage = formPage.getEditorSite().getPage();
				
				try {
					workbenchPage.showView(ImportsExportsView.VIEW_ID);
					FormEditor editor = formPage.getEditor();
					if(EditorUtils.saveEditorIfDirty(editor, "Analyse Imports", "The editor content must be saved before continuing.")) {
						AnalyseImportsJob job = new AnalyseImportsJob("Analyse Imports", file, workbenchPage);
						job.schedule();
					}
				} catch (PartInitException e) {
					ErrorDialog.openError(workbenchPage.getWorkbenchWindow().getShell(), "Analyse Packages", null, new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error opening Imports/Exports view", e));
				}
			}
		});
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				managedForm.fireSelectionChanged(PkgPatternsListPart.this, event.getSelection());
				boolean enabled = !viewer.getSelection().isEmpty();
				btnInsert.setEnabled(enabled);
				btnRemove.setEnabled(enabled);
				btnMoveUp.setEnabled(enabled);
				btnMoveDown.setEnabled(enabled);
			}
		});
		btnAdd.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doAdd();
			}
		});
		btnInsert.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doInsert();
			}
		});
		btnRemove.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doRemove();
			}
		});
		btnMoveUp.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doMoveUp();
			}
		});
		btnMoveDown.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				doMoveDown();
			}
		});

		
		// Layout
		GridLayout layout;
		layout = new GridLayout(2, false);
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		composite.setLayout(layout);
		lnkAnalyse.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 2, 1));
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 6));
		btnAdd.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
		btnInsert.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
		btnRemove.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
		btnMoveUp.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
		btnMoveDown.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
	}
	protected List<HeaderClause> getClauses() {
		return clauses;
	}
	protected void doAddClause(HeaderClause clause) {
		clauses.add(clause);
		viewer.add(clause);
		
		validate();
		markDirty();
	}
	protected void doAddClauses(Collection<? extends HeaderClause> clauses) {
		if(clauses != null && !clauses.isEmpty()) {
			this.clauses.addAll(clauses);
			viewer.add(clauses.toArray(new HeaderClause[0]));
			
			validate();
			markDirty();
		}
	}
	protected void doRemoveClause(Object clause) {
		clauses.remove(clause);
		viewer.remove(clause);

		validate();
		markDirty();
	}
	protected void doAdd() {
		HeaderClause newPattern = new HeaderClause("", new HashMap<String, String>());
		doAddClause(newPattern);
		
		viewer.setSelection(new StructuredSelection(newPattern));
		validate();
		markDirty();
	}
	protected void doInsertClauses(Collection<? extends HeaderClause> newClauses) {
		if(newClauses != null && !newClauses.isEmpty()) {
			int selectedIndex;
			IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
			if(selection.isEmpty())
				return;
			selectedIndex = this.clauses.indexOf(selection.getFirstElement());
	
			this.clauses.addAll(selectedIndex, newClauses);
			viewer.setInput(this.clauses);
			viewer.setSelection(new StructuredSelection(newClauses.iterator().next()));
			
			validate();
			markDirty();
		}
	}
	protected void doInsert() {
		HeaderClause pattern = new HeaderClause("", new HashMap<String, String>());
		doInsertClauses(Arrays.asList(pattern));
	}
	protected void doRemove() {
		@SuppressWarnings("unchecked")
		Iterator iter = ((IStructuredSelection) viewer.getSelection()).iterator();
		while(iter.hasNext()) {
			Object item = iter.next();
			doRemoveClause(item);
		}
		validate();
		markDirty();
	}
	void doMoveUp() {
		int[] selectedIndexes = findSelectedIndexes();
		CollectionUtils.moveUp(clauses, selectedIndexes);
		viewer.refresh();
		validate();
		markDirty();
	}
	void doMoveDown() {
		int[] selectedIndexes = findSelectedIndexes();
		CollectionUtils.moveDown(clauses, selectedIndexes);
		viewer.refresh();
		validate();
		markDirty();
	}
	int[] findSelectedIndexes() {
		Object[] selection = ((IStructuredSelection) viewer.getSelection()).toArray();
		int[] selectionIndexes = new int[selection.length];
		
		for(int i=0; i<selection.length; i++) {
			selectionIndexes[i] = clauses.indexOf(selection[i]);
		}
		return selectionIndexes;
	}
	/*
	void resetSelection(int[] selectedIndexes) {
		ArrayList<ImportPattern> selection = new ArrayList<ImportPattern>(selectedIndexes.length);
		for (int index : selectedIndexes) {
			selection.add(patterns.get(index));
		}
		viewer.setSelection(new StructuredSelection(selection), true);
	}
	*/
;	@Override
	public void initialize(IManagedForm form) {
		super.initialize(form);
		
		this.managedForm = form;
		this.model = (BndEditModel) form.getInput();
		this.model.addPropertyChangeListener(Constants.IMPORT_PACKAGE, this);
	}
	@Override
	public void dispose() {
		super.dispose();
		this.model.removePropertyChangeListener(Constants.IMPORT_PACKAGE, this);
		imgAnalyse.dispose();
	}
	@Override
	public void refresh() {
		super.refresh();
		
		// Deep-copy the model
		Collection<HeaderClause> original = model.getHeaderClauses(propertyName);
		if(original != null) {
			clauses = new ArrayList<HeaderClause>(original.size());
			for (HeaderClause clause : original) {
				clauses.add(clause.clone());
			}
		} else {
			clauses = new ArrayList<HeaderClause>();
		}
		viewer.setInput(clauses);
		validate();
	}
	public void validate() {
		// Do nothing.
	}
	@Override
	public void commit(boolean onSave) {
		try {
			model.removePropertyChangeListener(propertyName, this);
			model.setHeaderClauses(propertyName, clauses.isEmpty() ? null : clauses);
		} finally {
			super.commit(onSave);
			model.addPropertyChangeListener(propertyName, this);
		}
	}
	public void propertyChange(PropertyChangeEvent evt) {
		IFormPage page = (IFormPage) managedForm.getContainer();
		if(page.isActive())
			refresh();
		else
			markStale();
	}
	public void updateLabels(Object[] elements) {
		viewer.update(elements, null);
	}
}