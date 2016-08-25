package org.protege.editor.owl.integration;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.ChangeListMinimizer;
import org.protege.editor.owl.model.OWLEditorKitHook;
import org.protege.editor.owl.model.OWLModelManager;
import org.protege.editor.owl.model.event.EventType;
import org.protege.editor.owl.model.event.OWLModelManagerChangeEvent;
import org.protege.editor.owl.model.event.OWLModelManagerListener;
import org.protege.editor.owl.model.history.HistoryManager;
import org.protege.editor.owl.model.history.ReverseChangeGenerator;
import org.protege.editor.owl.model.history.UndoManagerListener;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyChangeListener;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Author: Matthew Horridge<br>
 * The University Of Manchester<br>
 * Medical Informatics Group<br>
 * Date: 19-May-2006<br><br>

 * matthew.horridge@cs.man.ac.uk<br>
 * www.cs.man.ac.uk/~horridgm<br><br>
 */
public class SessionRecorder implements OWLOntologyChangeListener, HistoryManager {

	public static String SRID = "org.protege.editor.owl.client.SessionRecorder";

	private enum ChangeType {
		UNDOING, REDOING, NORMAL
	}

	private ChangeType typeOfChangeInProgress = ChangeType.NORMAL;

	private Logger logger = LoggerFactory.getLogger(HistoryManager.class);

	private OWLOntology ontology;

	private boolean enabled = true;

	private Map<OWLOntologyID, List<Stack<List<OWLOntologyChange>>>> stash = new HashMap<>();
	
	public SessionRecorder(OWLOntology ont) {
		ontology = ont;
		reset();
		
	}


	/**
	 * Holds a list of sets of changes that can be undone within
	 * a given ontology.
	 * These are a list of "forward" changes - in other words
	 * if the list contain an "add superclass" history, then the
	 * required undo history is a "remove superclass" history.
	 */
	private Stack<List<OWLOntologyChange>> undoStack;



	/**
	 * Holds a list of sets of changes that can be redone. These
	 * are changes that result from an undo operation.
	 * These are a list of "forward" changes rather that the
	 * "undo changes".
	 */
	private Stack<List<OWLOntologyChange>> redoStack;

	private List<UndoManagerListener> listeners = new ArrayList<UndoManagerListener>();

	

	public void reset() {
		undoStack = new Stack<>();
		redoStack = new Stack<>();
		OWLOntologyID ontologyId = ontology.getOntologyID();
		ArrayList<Stack<List<OWLOntologyChange>>> reset_stack = new ArrayList<Stack<List<OWLOntologyChange>>>();
		reset_stack.add(undoStack);
		reset_stack.add(redoStack);
		stash.put(ontologyId, reset_stack);

		//listeners = new ArrayList<>();
		typeOfChangeInProgress = ChangeType.NORMAL;
		fireStateChanged();
	}

	public boolean canRedo() {
		return redoStack.size() > 0;
	}


	public boolean canUndo() {
		return undoStack.size() > 0;
	}


	public void logChanges(List<? extends OWLOntologyChange> changes) {
		switch (typeOfChangeInProgress) {
		case NORMAL:
			// Clear the redo stack, because we can
			// no longer redo
			redoStack.clear();
			// no break;
		case REDOING:
			// Push the changes onto the stack
			undoStack.push(new ArrayList<>(changes));
			break;
		case UNDOING:
			// In undo mode, so handleSave changes for redo.
			// Push the changes onto the redo stack.  Since these will
			// be undo changes, we need to get hold of the reverse changes
			// (The stacks, both undo and redo, should always hold the forward
			// changes).

			redoStack.push(reverseChanges(changes));
			break;
		}
		fireStateChanged();
	}


	public void redo() {
		if (canRedo()) {
			try {
				typeOfChangeInProgress = ChangeType.REDOING;
				List<OWLOntologyChange> redoChanges = redoStack.pop();
				ontology.getOWLOntologyManager().applyChanges(redoChanges);
			}
			catch (Exception e) {
				logger.error("An error occurred whilst redoing the last set of undone changes.", e);
			}
			finally {
				typeOfChangeInProgress = ChangeType.NORMAL;
			}
		}
	}


	public void undo() {
		if (canUndo()) {
			try {
				typeOfChangeInProgress = ChangeType.UNDOING;
				// Attempt to undo the changes
				List<OWLOntologyChange> changes = undoStack.pop();

				// Apply the changes
				ontology.getOWLOntologyManager().applyChanges(reverseChanges(changes));
				//                // Remove changes from log
				//                removeChanges(changes);
			}
			catch (Exception e) {
				logger.error("An error occurred whilst attempting to undo the last set of changes.", e);
			}
			finally {
				// No longer in undo mode
				typeOfChangeInProgress = ChangeType.NORMAL;
			}
		}
	}


	public void addUndoManagerListener(UndoManagerListener listener) {
		listeners.add(listener);
	}


	public void removeUndoManagerListener(UndoManagerListener listener) {
		listeners.remove(listener);
	}


	public List<List<OWLOntologyChange>> getLoggedChanges() {
		List<List<OWLOntologyChange>> copyOfLog = new ArrayList<>();
		for (List<OWLOntologyChange> changes : undoStack){
			copyOfLog.add(new ArrayList<>(changes));
		}
		return copyOfLog;
	}


	public void fireStateChanged() {
		for (UndoManagerListener listener : new ArrayList<>(listeners)) {
			listener.stateChanged(this);
		}
	}

	private List<OWLOntologyChange> reverseChanges(List<? extends OWLOntologyChange> changes) {
		List<OWLOntologyChange> reversedChanges = new ArrayList<>();
		for (OWLOntologyChange change : changes) {
			ReverseChangeGenerator gen = new ReverseChangeGenerator();
			change.accept(gen);
			// Reverse the order
			reversedChanges.add(0, gen.getReverseChange());
		}
		return reversedChanges;
	}



	
	@Override
	public void ontologiesChanged(List<? extends OWLOntologyChange> changes) throws OWLException {

		if (enabled) {
			logChanges(changes);
		}

	}

	public void stopRecording() {
		enabled = false;
	}

	/**
	 * Call this method to start listening to changes that are being applied to the current ontology
	 */
	public void startRecording() {
		enabled = true;
	}

	public List<OWLOntologyChange> getUncommittedChanges() {

		// Flatten the stack
		List<OWLOntologyChange> toReturn = new ArrayList<>();
		for (List<OWLOntologyChange> changes : undoStack) {
			toReturn.addAll(changes);
		}
		return new ChangeListMinimizer().getMinimisedChanges(toReturn);
	}

	@Override
	public void clear() {
		reset();
	}

}

