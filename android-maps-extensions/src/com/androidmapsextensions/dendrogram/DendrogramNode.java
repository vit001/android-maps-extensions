/*
 * This file is licensed to You under the "Simplified BSD License".
 * You may not use this software except in compliance with the License. 
 * You may obtain a copy of the License at
 *
 * http://www.opensource.org/licenses/bsd-license.php
 * 
 * See the COPYRIGHT file distributed with this work for information
 * regarding copyright ownership.
 */
package com.androidmapsextensions.dendrogram;

import com.androidmapsextensions.impl.ClusterMarker;
import com.google.android.gms.maps.model.LatLng;


/**
 * A DendrogramNode is a node in a Dendrogram.
 * It represents a subtree of the dendrogram tree.
 * It has two children (left and right), 
 * and it can provide the number of leaf nodes (ObservationNodes) in this subtree.
 * 
 * @author Matthias.Hauswirth@unisi.ch
 */
public abstract class DendrogramNode {
	
	public abstract DendrogramNode getLeft();
	public abstract DendrogramNode getRight();
	public abstract int getObservationCount();

	private MergeNode parent;
	protected double[] position;	
	private ClusterMarker clusterMarker;

	public final MergeNode getParent() {
		return parent;
	}
		
	public final void setParent( MergeNode parent ) {
		this.parent = parent;
	}

	public double[] getPosition() {
		return position;
	}
	
	public void setPosition( double[] position ) {
		this.position = position;
	}
	
	public LatLng getLatLng() {
		return new LatLng( position[0], position[1] );
	}

	public ClusterMarker getClusterMarker() {
		return clusterMarker;
	}

	public void setClusterMarker( ClusterMarker clusterMarker ) {
		this.clusterMarker = clusterMarker;
	}

	// When the camera zoom level is between min (inclusive) and max (exclusive) this node will be rendered
	private float minZoomRendered;
	private float maxZoomRendered;
	public float getMinZoomRendered() {
		return minZoomRendered;
	}
	public float getMaxZoomRendered() {
		return maxZoomRendered;
	}
	public void setMinZoomRendered( float zoom ) {
		minZoomRendered = zoom;
	}
	public void setMaxZoomRendered( float zoom ) {
		maxZoomRendered = zoom;
	}
}