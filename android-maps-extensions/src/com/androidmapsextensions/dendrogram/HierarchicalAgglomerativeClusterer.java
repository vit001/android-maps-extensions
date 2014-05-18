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

import org.csdgn.util.KDTree;
import org.csdgn.util.ResultHeap;

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
    //private DistanceFunction df = new SquareEuclideanDistanceFunction();
    
	// TODO - What about points which should never be clustered?
    public void cluster( final DendrogramBuilder clusteringBuilder ) {
    	// We cannot have duplicate keys in kdtree. Hence, first cluster all dupes.
    	// TODO - what if there are triples? 
    	
    	Set<Integer> dupeNodes = new HashSet<Integer>();
    	/*
    	{
    		for ( int i = 0; i < experiment.getNumberOfObservations(); ++i ) {
    			double[] pos1 = experiment.getPosition(i);
    			for ( int j = i+1; j < experiment.getNumberOfObservations(); ++j ) {
    				double[] pos2 = experiment.getPosition(j);
    				if ( pos1[0] == pos2[0]  &&  pos1[1] == pos2[1] ) {
    					ObservationNode node1 = new ObservationNode(i, pos1);
    					ObservationNode node2 = new ObservationNode(j, pos2);
    					clusteringBuilder.merge( node1, node2, 0 );
    					dupeNodes.add( i );
    				}
    			}
    		}
    	}
    	*/
    	// Initialize the KD-tree    	
    	kd = new KDTree<DendrogramNode>(2);
    	for ( int i = 0; i < mExperiment.getNumberOfObservations(); ++i ) {
    		if ( dupeNodes.contains( i ) ) {
    			continue;
    		}
    		double [] xyCoord = mExperiment.getPosition( i );
    		Log.e( "e", "adding i=" + i + " x=" + xyCoord[0] + " y=" + xyCoord[1] );
    		kd.add( xyCoord, new ObservationNode(i, xyCoord) );
    	} 
    	
    	// Initialize the min-heap
    	Log.e("e","Initializing min-heap");
    	SortedMap<Double,Pair> minHeap = new TreeMap<Double,Pair>();
    	
    	for ( int i = 0; i < mExperiment.getNumberOfObservations(); ++i ) {
    		if ( dupeNodes.contains( i ) ) {
    			continue;
    		}

    		double [] pos = mExperiment.getPosition( i );
    		DendrogramNode target = kd.getNearestNeighbors( pos, 1 ).removeMax();
    		DendrogramNode nearest = findNearest( target );
    		
    		//double dist = dissimilarityMeasure.computeDissimilarity( experiment, target.getObservationCount(), nearest.getObservationCount() );    			
    		double dist = mDissimilarityMeasure.distanceMiles( pos, nearest.getPosition() );
    		minHeap.put( dist, new Pair( target, nearest ) );
    	}
    	
    	Log.e("e","Constructing dendrogram");
    	int clustersCreated = 0;
    	Set<DendrogramNode> deletedNodes = new HashSet<DendrogramNode>();
    	while ( clustersCreated < mExperiment.getNumberOfObservations()-1 ) {
    		Pair pair = minHeap.remove( minHeap.firstKey() );
    		// Does kd contain pair.A ?
    		DendrogramNode node1 = pair.mCluster1;
    		DendrogramNode node2 = pair.mCluster2;
    		
    		if ( deletedNodes.contains( node1 ) ) { 
    			// A was already clustered with somebody
    		}
    		else
   			if ( deletedNodes.contains( node2 ) ) {
   				//Log.e("e","node2 is null");
   	    		double [] pos1 = node1.getPosition();

   				// B is invalid, find new best match for A
   				DendrogramNode nearest = findNearest( node1 );
   				//double dist = dissimilarityMeasure.computeDissimilarity( experiment, node1.getObservationCount(), nearest.getObservationCount() );
   				double dist = mDissimilarityMeasure.distanceMiles( pos1, nearest.getPosition() );
   				minHeap.put( dist, new Pair(node1, nearest) );
   			} else {
   	    		double [] pos1 = node1.getPosition();
   	    		double [] pos2 = node2.getPosition();
   	    		
   				//double dist = dissimilarityMeasure.computeDissimilarity( experiment, node1.getObservationCount(), node2.getObservationCount() );
   				double dist = mDissimilarityMeasure.distanceMiles( pos1, pos2 );
   				MergeNode cluster = clusteringBuilder.merge( pair.mCluster1, pair.mCluster2, dist );
   				++clustersCreated;
   				
   				kd.delete( pos1, node1 );
   				kd.delete( pos2, node2 );
   				deletedNodes.add( node1 );
   				deletedNodes.add( node2 );
   				
   				kd.add( cluster.getPosition(), cluster );
   				
   				if ( clustersCreated >= mExperiment.getNumberOfObservations()-1 ) {
   					break;
   				}
   				
   				DendrogramNode nearest = findNearest( cluster );
   				//double dist2 = dissimilarityMeasure.computeDissimilarity( experiment, cluster.getPosition(), nearest.getObservationCount() );
   				double dist2 = mDissimilarityMeasure.distanceMiles( cluster.getPosition(), nearest.getPosition() );
   				minHeap.put( dist2, new Pair(cluster, nearest) );
   			}
    	}
    }

    // Find the nearest observation to this observation, excluding self
    // Ignore deleted observations
    //private final Set<DendrogramNode> deletedNodes = new HashSet<DendrogramNode>();
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