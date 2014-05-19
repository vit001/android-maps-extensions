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
 * An ObservationNode represents a leaf node in a Dendrogram.
 * It corresponds to a singleton cluster of one observation.
 * 
 * @author Matthias.Hauswirth@usi.ch
 */
public final class ObservationNode extends DendrogramNode {

	private final int observation;
	private MergeNode parent;
	private final double[] position;	
	private ClusterMarker clusterMarker;
	
	public ObservationNode( final int observation, final double[] position ) {
		this.observation = observation;
		this.position    = position;
	}
	
	@Override
	public final DendrogramNode getLeft() {
		return null;
	}
	
	@Override
	public final DendrogramNode getRight() {
		return null;
	}
	
	@Override
	public final MergeNode getParent() {
		return parent;
	}
		
	public final void setParent( MergeNode parent ) {
		this.parent = parent;
	}
	
	public int getObservationCount() {
		return 1;
	}
	
	public final int getObservation() {
		return observation;
	}

	@Override
	public double[] getPosition() {
		return position;
	}
	
	@Override
	public LatLng getLatLng() {
		return new LatLng( position[0], position[1] );
	}
	
	@Override
	public ClusterMarker getClusterMarker() {
		return clusterMarker;
	}
	@Override
	public void setClusterMarker( ClusterMarker clusterMarker ) {
		this.clusterMarker = clusterMarker;
	}
}