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
 * A MergeNode represents an interior node in a Dendrogram.
 * It corresponds to a (non-singleton) cluster of observations.
 * 
 * @author Matthias.Hauswirth@usi.ch
 */
public final class MergeNode extends DendrogramNode {
	
	private final DendrogramNode left;
	private final DendrogramNode right;
	private final double dissimilarity;
	private final int observationCount;
		
	public MergeNode( final DendrogramNode left, final DendrogramNode right, double dissimilarity ) {
		
		this.left   = left;
		this.right  = right;
		observationCount = left.getObservationCount() + right.getObservationCount();
		
		double[] leftPos  = left.getPosition();
		double[] rightPos = right.getPosition();
		// TODO
		double newLat = ( leftPos[0]  * left.getObservationCount() +
					      rightPos[0] * right.getObservationCount() ) / observationCount;
		double newLon = ( leftPos[1]  * left.getObservationCount() +
			      		  rightPos[1] * right.getObservationCount() ) / observationCount; 
		this.position = new double[]{ newLat, newLon };
		this.dissimilarity = dissimilarity;
	}
	
	@Override
	public int getObservationCount() {
		return observationCount;
	}
	
	@Override
	public final DendrogramNode getLeft() {
		return left;
	}
	
	@Override
	public final DendrogramNode getRight() {
		return right;
	}
	
	public final double getDissimilarity() {
		return dissimilarity;
	}
}