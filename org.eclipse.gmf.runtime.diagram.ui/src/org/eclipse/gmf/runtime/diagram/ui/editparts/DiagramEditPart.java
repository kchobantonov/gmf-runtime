/******************************************************************************
 * Copyright (c) 2002, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation 
 ****************************************************************************/

package org.eclipse.gmf.runtime.diagram.ui.editparts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.draw2d.FreeformLayer;
import org.eclipse.draw2d.FreeformLayout;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.LayoutListener;
import org.eclipse.draw2d.TreeSearch;
import org.eclipse.draw2d.Viewport;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.CompoundSnapToHelper;
import org.eclipse.gef.DragTracker;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPolicy;
import org.eclipse.gef.LayerConstants;
import org.eclipse.gef.Request;
import org.eclipse.gef.SnapToGeometry;
import org.eclipse.gef.SnapToGrid;
import org.eclipse.gef.SnapToHelper;
import org.eclipse.gef.editpolicies.RootComponentEditPolicy;
import org.eclipse.gef.editpolicies.SnapFeedbackPolicy;
import org.eclipse.gef.requests.SelectionRequest;
import org.eclipse.gef.rulers.RulerProvider;
import org.eclipse.gef.tools.DeselectAllTracker;
import org.eclipse.gmf.runtime.diagram.ui.editpolicies.ContainerEditPolicy;
import org.eclipse.gmf.runtime.diagram.ui.editpolicies.ContainerNodeEditPolicy;
import org.eclipse.gmf.runtime.diagram.ui.editpolicies.CreationEditPolicy;
import org.eclipse.gmf.runtime.diagram.ui.editpolicies.DiagramDragDropEditPolicy;
import org.eclipse.gmf.runtime.diagram.ui.editpolicies.DiagramPopupBarEditPolicy;
import org.eclipse.gmf.runtime.diagram.ui.editpolicies.EditPolicyRoles;
import org.eclipse.gmf.runtime.diagram.ui.editpolicies.XYLayoutEditPolicy;
import org.eclipse.gmf.runtime.diagram.ui.internal.editparts.ISurfaceEditPart;
import org.eclipse.gmf.runtime.diagram.ui.internal.figures.PageBreaksFigure;
import org.eclipse.gmf.runtime.diagram.ui.internal.pagesetup.PageInfoHelper;
import org.eclipse.gmf.runtime.diagram.ui.internal.properties.WorkspaceViewerProperties;
import org.eclipse.gmf.runtime.diagram.ui.internal.ruler.SnapToGuidesEx;
import org.eclipse.gmf.runtime.diagram.ui.internal.tools.RubberbandDragTracker;
import org.eclipse.gmf.runtime.diagram.ui.parts.DiagramGraphicalViewer;
import org.eclipse.gmf.runtime.draw2d.ui.figures.AnimatableLayoutListener;
import org.eclipse.gmf.runtime.notation.Diagram;
import org.eclipse.gmf.runtime.notation.View;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Scrollable;

/**
 * Controller for the diagram 
 *  
 * @author jcorchis
 *
 */

public class DiagramEditPart
	extends GraphicalEditPart
	implements LayerConstants, ISurfaceEditPart {
	private boolean shouldUpdatePageBreakLocation = false;
	private boolean isSupportingViewActions = true;
	
	/**
	 * construcotr
	 * @param diagramView the view controlled by this edit part
	 */
	public DiagramEditPart(View diagramView) {
		super(diagramView);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.gmf.runtime.diagram.ui.editparts.GraphicalEditPart#createDefaultEditPolicies()
	 */
	protected void createDefaultEditPolicies() {
		super.createDefaultEditPolicies();
		installEditPolicy(
			EditPolicyRoles.CREATION_ROLE,
			new CreationEditPolicy());
		installEditPolicy(EditPolicy.CONTAINER_ROLE, new ContainerEditPolicy());
		installEditPolicy(
			EditPolicy.COMPONENT_ROLE,
			new RootComponentEditPolicy());
		installEditPolicy(EditPolicy.LAYOUT_ROLE, new XYLayoutEditPolicy());
		installEditPolicy(
			EditPolicyRoles.DRAG_DROP_ROLE,
			new DiagramDragDropEditPolicy());
		installEditPolicy(
			EditPolicy.GRAPHICAL_NODE_ROLE,
			new ContainerNodeEditPolicy());
		installEditPolicy(EditPolicyRoles.SNAP_FEEDBACK_ROLE,
				new SnapFeedbackPolicy());
		installEditPolicy(EditPolicyRoles.POPUPBAR_ROLE,
			new DiagramPopupBarEditPolicy());
	}
	
	/**
	 * @author mmostafa
	 * PageBreaksLayoutListener Listens to post layout so it can update the page breaks  
	 */
	private class PageBreaksLayoutListener extends LayoutListener.Stub {

		public void postLayout(IFigure container) {
			super.postLayout(container);
			updatePageBreaksLocation();
		}
		
		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.gef.editparts.AbstractGraphicalEditPart#createFigure()
	 */
	protected IFigure createFigure() {

		// Override the containsPoint and findFigureAt methods
		// to treat this layer (Primary Layer) as if it were opaque.

		// This is for the grid layer so that it can be seen beneath the
		// figures.
		IFigure f = new FreeformLayer() {	
			/* (non-Javadoc)
			 * @see org.eclipse.draw2d.Layer#containsPoint(int, int)
			 */
			public boolean containsPoint(int x, int y) {
				return getBounds().contains(x, y);
			}

			/* (non-Javadoc)
			 * @see org.eclipse.draw2d.Layer#findFigureAt(int, int, org.eclipse.draw2d.TreeSearch)
			 */
			public IFigure findFigureAt(int x, int y, TreeSearch search) {
				if (!isEnabled())
					return null;
				if (!containsPoint(x, y))
					return null;
				if (search.prune(this))
					return null;
				IFigure child = findDescendantAtExcluding(x, y, search);
				if (child != null)
					return child;
				if (search.accept(this))
					return this;
				return null;
			}

			/* (non-Javadoc)
			 * @see org.eclipse.draw2d.Figure#validate()
			 */
			public void validate() {				
				super.validate();
				if (shouldUpdatePageBreakLocation){
					shouldUpdatePageBreakLocation = false;
					updatePageBreaksLocation();
				}
			}
		};
		f.setLayoutManager(new FreeformLayout());
		f.addLayoutListener(new AnimatableLayoutListener());
		f.addLayoutListener(new PageBreaksLayoutListener());
		return f;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.gmf.runtime.diagram.ui.editparts.GraphicalEditPart#getDiagramView()
	 */
	public Diagram getDiagramView() {
		return (Diagram)getModel();
	}

	public DragTracker getDragTracker(Request req) {
		if (req instanceof SelectionRequest
			&& ((SelectionRequest) req).getLastButtonPressed() == 3)
			return new DeselectAllTracker(this);
		return new RubberbandDragTracker();
	}

	public IFigure getLayer(Object layer) {
		return super.getLayer(layer);
	}

	/**
	 * Return the Scrollable Control of this edit part's Viewer
	 * @return <code>Scrollable</code>
	 */
	public Scrollable getScrollableControl() {
		return (Scrollable) getViewer().getControl();
	}

	/**
	 * getter for this Edit Part's figure <code>Viewport</code>
	 * @return the view port
	 */
	public Viewport getViewport() {
		IFigure fig = getFigure().getParent();
		while (fig != null) {
			if (fig instanceof Viewport)
				return (Viewport) fig;
			fig = fig.getParent();
		}
		return null;
	}

	/**
	 * getter for the connection layer
	 * @return the connection layer
	 */
	protected IFigure getConnectionLayer() {
		return getLayer(LayerConstants.CONNECTION_LAYER);
	}

	/**
	 * returns all connections owned by this diagram, the returned list is a
	 * list of <code>ConnectionEditPart</code>s
	 * 
	 * @return list of <code>ConnectionEditPart</code>s
	 */
	public List getConnections() {
		Iterator views = getDiagramView().getEdges().iterator(); 

		Map registry = getViewer().getEditPartRegistry();
		List connections = new ArrayList();
		while (views.hasNext()) {
			Object connectionEP = registry.get(views.next());
			if (connectionEP != null)
				connections.add(connectionEP);
		}
		return connections;
	}

	/**
	 * @return List of primary edit parts.  If there are none then it returns
	 * a Collections.EMPTY_LIST, which is immutable
	 */
	public List getPrimaryEditParts() {
		List connections = getConnections();
		List shapes = getChildren();
		if (connections.size() > 0 || shapes.size() > 0) {
			List myChildren = new ArrayList();
			myChildren.addAll(shapes);
			myChildren.addAll(connections);
			return myChildren;
		}
		return Collections.EMPTY_LIST;
	}

	public Map getAppearancePropertiesMap() {
		return Collections.EMPTY_MAP;
	}

	/**
	 * Adds a figure listener to each figure that is added to the diagram so,
	 * the the page breaks can be notified of changes.
	 * @see org.eclipse.gef.editparts.AbstractEditPart#addChildVisual(EditPart, int)
	 */
	protected void addChildVisual(EditPart childEditPart, int index) {
		final IFigure child = ((GraphicalEditPart) childEditPart).getFigure();
		getContentPane().add(child, index);
	}
	
	/**
	 * Updates the Viewer's preference store page breaks location.
	 */
	protected void updatePageBreaksLocation() {
		if ( getParent() == null || getRoot() == null ) {
			return;
		}
		// do not update unless we really need to
		IPreferenceStore preferenceStore = ((DiagramRootEditPart) getRoot()).getWorkspaceViewerPreferences();
		// do not update unless we really need to
		if (preferenceStore == null ||
			preferenceStore.getBoolean(WorkspaceViewerProperties.VIEWPAGEBREAKS)==false)
			return;

		((DiagramRootEditPart) getRoot())
				.getPageBreakEditPart().resize(getChildrenBounds());
		
		Rectangle r =
			((DiagramRootEditPart) getRoot())
				.getPageBreakEditPart()
				.getFigure()
				.getBounds();
		((DiagramGraphicalViewer) getViewer())
			.getWorkspaceViewerPreferenceStore()
			.setValue(WorkspaceViewerProperties.PAGEBREAK_X, r.x);
		((DiagramGraphicalViewer) getViewer())
			.getWorkspaceViewerPreferenceStore()
			.setValue(WorkspaceViewerProperties.PAGEBREAK_Y, r.y);
	}

	/**
	 * Returns the bounds of the <code>PRINTABLE_LAYERS</code>
	 * @return rectangle bounds of the diagram's children
	 */
	public Rectangle getChildrenBounds() {
		return PageInfoHelper.getChildrenBounds(this, PageBreaksFigure.class);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.gmf.runtime.diagram.ui.editparts.GraphicalEditPart#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class adapter) {

		if (adapter == SnapToHelper.class) {

			List snapStrategies = new ArrayList();

			Boolean val = (Boolean)getViewer().getProperty(RulerProvider.PROPERTY_RULER_VISIBILITY);
			if (val != null && val.booleanValue())
				snapStrategies.add(new SnapToGuidesEx(this));

			val = (Boolean)getViewer().getProperty(SnapToGeometry.PROPERTY_SNAP_ENABLED);
			if (val != null && val.booleanValue())
				snapStrategies.add(new SnapToGrid(this));

			if (snapStrategies.size() == 0)
				return null;

			if (snapStrategies.size() == 1)
				return (SnapToHelper)snapStrategies.get(0);

			SnapToHelper ss[] = new SnapToHelper[snapStrategies.size()];
			for (int i = 0; i < snapStrategies.size(); i++)
				ss[i] = (SnapToHelper)snapStrategies.get(i);
			return new CompoundSnapToHelper(ss);
		}

		return super.getAdapter(adapter);
	}
	
	/**
	 * gets a list of all children that could affect the zoom capability
	 * @return list of <code>View</code>s
	 */
	public List getChildrenAffectingZoom(){
		return new ArrayList(getChildren());
	}
	
	/**
	 * Refreshes the page breaks.
	 */
	public void refreshPageBreaks() {
		if ( getRoot() == null ) {
			return;
		}
		
		((DiagramRootEditPart)getRoot()).refreshPageBreaks();
	}
	
	
	
	/* (non-Javadoc)
	 * @see org.eclipse.gmf.runtime.diagram.ui.internal.editparts.ISurfaceEditPart#isSupportingViewActions()
	 */
	public boolean isSupportingViewActions(){
		return this.isSupportingViewActions;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.gmf.runtime.diagram.ui.internal.editparts.ISurfaceEditPart#setIsSupportingViewActions(boolean)
	 */
	public void setIsSupportingViewActions(boolean supportsViewActions){
		this.isSupportingViewActions = supportsViewActions;
	}
}
