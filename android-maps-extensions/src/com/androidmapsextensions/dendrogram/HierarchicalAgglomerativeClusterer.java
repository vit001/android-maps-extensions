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

import android.util.Log;

import com.androidmapsextensions.kdtree.KDTree;
import com.androidmapsextensions.kdtree.ResultHeap;


import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;


/**
 * The HierarchicalAgglomerativeClusterer creates a hierarchical agglomerative clustering.
 * 
 * <pre>
 * Experiment experiment = ...;
 * DissimilarityMeasure dissimilarityMeasure = ...;
 * AgglomerationMethod agglomerationMethod = ...;
 * DendrogramBuilder dendrogramBuilder = new DendrogramBuilder(experiment.getNumberOfObservations());
 * HierarchicalAgglomerativeClusterer clusterer = new HierarchicalAgglomerativeClusterer(experiment, dissimilarityMeasure, agglomerationMethod);
 * clusterer.cluster(dendrogramBuilder);
 * Dendrogram dendrogram = dendrogramBuilder.getDendrogram();
 * </pre>
 * 
 * @author Matthias.Hauswirth@usi.ch
 */
public final class HierarchicalAgglomerativeClusterer {

    private Experiment           mExperiment;
    private DissimilarityMeasure mDissimilarityMeasure;    
        
    public HierarchicalAgglomerativeClusterer( final Experiment experiment, final DissimilarityMeasure dissimilarityMeasure ) {
        this.mExperiment = experiment;
        this.mDissimilarityMeasure = dissimilarityMeasure;
    }
    
    public void setExperiment( final Experiment experiment ) {
        this.mExperiment = experiment;
    }
    
    public Experiment getExperiment() {
        return mExperiment;
    }
    
    public void setDissimilarityMeasure( final DissimilarityMeasure dissimilarityMeasure ) {
        this.mDissimilarityMeasure = dissimilarityMeasure;
    }

    public DissimilarityMeasure getDissimilarityMeasure() {
        return mDissimilarityMeasure;
    }
    
    private KDTree<DendrogramNode> kd;
    
    // Implementation of fast clustering algorithm from:
    // https://engineering.purdue.edu/~milind/docs/rt08.pdf
    // Duplicate keys (markers with identical position) are allowed.
    public void cluster( DendrogramBuilder clusteringBuilder, int clusterGroup ) {
    	
    	// Initialize the KD-tree
    	int nObservations = 0;
    	kd = new KDTree<DendrogramNode>( 2 );
    	for ( int i = 0; i < mExperiment.getNumberOfObservations(); ++i ) {
    		if ( mExperiment.getClusterGroup( i ) != clusterGroup ) {
    			continue;
    		}
    		double [] xyCoord = mExperiment.getPosition( i );
    		kd.add( xyCoord, new ObservationNode(i, xyCoord) );
    		++nObservations;
    	}
    	
    	// Initialize the min-heap
    	SortedMap<Double,Pair> minHeap = new TreeMap<Double,Pair>();    	
    	for ( int i = 0; i < mExperiment.getNumberOfObservations(); ++i ) {
    		if ( mExperiment.getClusterGroup( i ) != clusterGroup ) {
    			continue;
    		}
    		
    		double [] pos = mExperiment.getPosition( i );
    		DendrogramNode target = kd.getNearestNeighbors( pos, 1 ).removeMax();
    		DendrogramNode nearest = findNearest( target );
    		
    		double dist = mDissimilarityMeasure.computeDissimilarity( mExperiment, target.getPosition(), nearest.getPosition() );
    		minHeap.put( dist, new Pair( target, nearest ) );
    	}
    	
    	Log.e("e","Constructing dendrogram");
    	int clustersCreated = 0;
    	Set<DendrogramNode> deletedNodes = new HashSet<DendrogramNode>();
    	while ( clustersCreated < nObservations-1 ) {
    		Pair pair = minHeap.remove( minHeap.firstKey() );
    		DendrogramNode node1 = pair.mCluster1;
    		DendrogramNode node2 = pair.mCluster2;
    		
    		// Does kd contain pair.A ?
    		if ( deletedNodes.contains( node1 ) ) { 
    			// A was already clustered with somebody
    		}
    		else
   			if ( deletedNodes.contains( node2 ) ) {
   	    		double [] pos1 = node1.getPosition();

   				// B is invalid, find new best match for A
   				DendrogramNode nearest = findNearest( node1 );
   				double dist = mDissimilarityMeasure.computeDissimilarity( mExperiment, pos1, nearest.getPosition() );
   				minHeap.put( dist, new Pair(node1, nearest) );
   			} else {
   	    		double [] pos1 = node1.getPosition();
   	    		double [] pos2 = node2.getPosition();
   	    		
   				double dist = mDissimilarityMeasure.computeDissimilarity( mExperiment, pos1, pos2 );
   				MergeNode cluster = clusteringBuilder.merge( pair.mCluster1, pair.mCluster2, dist );
   				++clustersCreated;
   				
   				kd.delete( pos1, node1 );
   				kd.delete( pos2, node2 );
   				deletedNodes.add( node1 );
   				deletedNodes.add( node2 );
   				
   				kd.add( cluster.getPosition(), cluster );
   				
   				if ( clustersCreated >= nObservations-1 ) {
   					break;
   				}
   				
   				DendrogramNode nearest = findNearest( cluster );
   				double dist2 = mDissimilarityMeasure.computeDissimilarity( mExperiment, cluster.getPosition(), nearest.getPosition() );
   				minHeap.put( dist2, new Pair(cluster, nearest) );
   			}
    	}
    }

    // Find the nearest observation to this observation, excluding self
    // Tree will ignore previously deleted observations
    private DendrogramNode findNearest( DendrogramNode A ) {
    	ResultHeap<DendrogramNode> res = kd.getNearestNeighbors( A.getPosition(), 2 );
    	DendrogramNode max, min;
    	max = res.removeMax();
    	if ( res.isEmpty() ) {
    		min = max;
    	} 
    	else {
    		min = res.removeMax();
    	}
    	if ( A.equals( min ) ) {
    		return max;
    	}
    	else
    	if ( A.equals( max ) ) {
    		return min;
    	}
    	else {
    		throw new IllegalStateException();
    	}
    }
	
    private static final class Pair {
    	
        private DendrogramNode mCluster1;
        private DendrogramNode mCluster2;
        
        public Pair ( final DendrogramNode cluster1, final DendrogramNode cluster2 ) {
            this.mCluster1 = cluster1;
            this.mCluster2 = cluster2;
        }
    }
}