package com.androidmapsextensions.impl;

import android.util.Log;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.androidmapsextensions.AnimationSettings;
import com.androidmapsextensions.ClusterOptions;
import com.androidmapsextensions.ClusterOptionsProvider;
import com.androidmapsextensions.ClusteringSettings;
import com.androidmapsextensions.Marker;
import com.androidmapsextensions.Marker.AnimationCallback;
import com.androidmapsextensions.dendrogram.Dendrogram;
import com.androidmapsextensions.dendrogram.DendrogramBuilder;
import com.androidmapsextensions.dendrogram.DendrogramNode;
import com.androidmapsextensions.dendrogram.DissimilarityMeasure;
import com.androidmapsextensions.dendrogram.Experiment;
import com.androidmapsextensions.dendrogram.HierarchicalAgglomerativeClusterer;
import com.androidmapsextensions.dendrogram.MergeNode;
import com.androidmapsextensions.dendrogram.ObservationNode;
import com.androidmapsextensions.kdtree.KDTree;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;


class HierarchicalClusteringStrategy implements ClusteringStrategy {

    private static boolean GOOGLE_PLAY_SERVICES_4_0 = true;
    
    private final MarkerOptions markerOptions = new MarkerOptions();
    
    private DelegatingGoogleMap factory;
    //private IGoogleMap map;
    private Map<DelegatingMarker, ClusterMarker> markers;
    //private double baseClusterSize;
    //private double clusterSize;
    private float oldZoom, zoom;
    
    private ClusterRefresher refresher;
    private ClusterOptionsProvider clusterOptionsProvider;
    
    private List<DelegatingMarker> fullMarkerList;
    private Dendrogram dendrogram;
    private KDTree<DendrogramNode> tree;
    
    // This is used for quickly determining which markers have been drawn, so onCameraChange we can
    // quickly remove unneeded ones.
    public Set<DendrogramNode> renderedNodes      = new HashSet<DendrogramNode>();
    // These nodes will be displayed once animation completes.
    public Set<DendrogramNode> pendingRenderNodes = new HashSet<DendrogramNode>();
    
    //TODO Do this in an async task so as not to block the map. Create a new dendrogram, then swap it out when done.
    // Also - queue up all marker actions in worker thread (flip of visibility, creation of new markers), then dequeue on 
    // main thread when done.
    // Also - make use of tree map for efficiency - only process markers in visible region by finding n nearest neighbors
    // and terminating processing once marker is outside visible region.
    private void reComputeDendrogram() {
    	Log.e("e","reComputingDendrogram with " + fullMarkerList.size() + " observations");
    	// Only markers within the same clusterGroup are clustered together
    	// TODO - for now, if clusterGroup < 0 => not clustered, otherwise consider all markers as in one group
    	cleanup();
    	// First, Compute the Dendrogram. Recompute it every time a marker is added or removed (or it's visibility changed).
    	Experiment experiment = new Experiment() {
			@Override
			public int getNumberOfObservations() {
				return fullMarkerList.size();
			}			
		    public double[] getPosition( int observation ) {
		    	DelegatingMarker pos = fullMarkerList.get( observation );
		    	LatLng ll = pos.getPosition();
		    	return new double[]{ ll.latitude, ll.longitude };
			}
		};
		DissimilarityMeasure dissimilarityMeasure = new DissimilarityMeasure() {
			private static final double EARTH_RADIUS_MILES = 3958.76;
			// Approximation for small distances, but good enough
			@Override
		    public double computeDissimilarity( Experiment experiment, int observation1, int observation2 ) {
				DelegatingMarker dm1 = fullMarkerList.get( observation1 );
				DelegatingMarker dm2 = fullMarkerList.get( observation2 );
				//if ( dm1.getClusterGroup() < 0  ||  dm2.getClusterGroup() < 0 ) {
				//	return Double.MAX_VALUE;
				//}
				
				double [] pos1 = experiment.getPosition( observation1 );
				double [] pos2 = experiment.getPosition( observation2 );
				
				return distanceMiles( pos1, pos2 );
			}
			@Override
			public double computeDissimilarity( Experiment experiment, double[] pos1, int observation2 ) {
				DelegatingMarker dm2 = fullMarkerList.get( observation2 );
				//if ( dm2.getClusterGroup() < 0 ) {
				//	return Double.MAX_VALUE;
				//} else {		
					return distanceMiles( pos1, experiment.getPosition( observation2 ) );
				//}
			}
			@Override
			public double distanceMiles( double[] pos1, double[] pos2 ) {
				double avgLat = Math.toRadians( (pos1[0] + pos2[0])/2 );
				
				double dx = Math.toRadians( pos2[1] - pos1[1] ) * Math.cos( avgLat );
				double dy = Math.toRadians( pos2[0] - pos1[0] );
				
				double d = EARTH_RADIUS_MILES * Math.sqrt( dx*dx + dy*dy ); 
				
				return d;
			}
		};
		DendrogramBuilder dendrogramBuilder = new DendrogramBuilder( experiment );
		
		HierarchicalAgglomerativeClusterer clusterer = new HierarchicalAgglomerativeClusterer( experiment, dissimilarityMeasure );
		clusterer.cluster( dendrogramBuilder );
		dendrogram = dendrogramBuilder.getDendrogram();
		tree = new KDTree<DendrogramNode>(2);
		// Add all nodes in the dendrogram to the tree
		
        if ( dendrogram == null )
        	return;
		addToTree( tree, dendrogram.getRoot() );
        
        cleanup();
		DendrogramNode rootNode = dendrogram.getRoot();
		computeMinMaxZoomRendered( rootNode );
		addClustersNowInVisibleRegion();
        refresher.refreshAll();
        
		Log.e("e","reComputingDendrogram DONE");
    }
    
    private void addToTree( KDTree<DendrogramNode> tree, DendrogramNode node ) {
    	if ( node == null )
    		return;
    	tree.add( node.getPosition(), node );
    	addToTree( tree, node.getLeft() );
    	addToTree( tree, node.getRight() );
    }
    
    void addToCluster( ClusterMarker cm, DendrogramNode node ) {
    	if ( node == null ) {
    	}
    	else 
    	if ( node instanceof MergeNode ) {
    		addToCluster( cm, node.getLeft() );
    		addToCluster( cm, node.getRight() );
    	} 
    	else
    	if ( node instanceof ObservationNode ) {
    		DelegatingMarker dm = fullMarkerList.get( ((ObservationNode) node).getObservation() );
    		cm.add( dm );
			markers.put( dm, cm );
    	}
    }
    
    // Walk the dendrogram and compute minZoomRendered, maxZoomRendered for each node.
    private void computeMinMaxZoomRendered( DendrogramNode node ) {
    	if ( node == null ) {
    		return;
    	}
    	else {
    		// The minimum zoom this node will be rendered is equal to the zoom when this node merges with it's sibling
    		MergeNode parent = node.getParent();
    		if ( parent == null ) {
    			node.setMinZoomRendered( 0 );
    		}
    		else {
        		double parentDissimilarity = parent.getDissimilarity();
        		node.setMinZoomRendered( thresholdToZoom( parentDissimilarity ) );        		
    		}
    		
    		// The maximum zoom this node will be rendered is equal to the zoom which produces a threshold
    		// equal to this dissimilarity
        	if ( node instanceof ObservationNode ) {
        		node.setMaxZoomRendered( Float.MAX_VALUE );
        	}
        	else
            if ( node instanceof MergeNode ) {
        		double dissimilarity = ((MergeNode) node).getDissimilarity();  
        		node.setMaxZoomRendered( thresholdToZoom( dissimilarity ) );
        		
        		Log.e("e","Node=" + node + " min=" + node.getMinZoomRendered() + " max=" + node.getMaxZoomRendered() );
        		
        		computeMinMaxZoomRendered( node.getLeft() );
        		computeMinMaxZoomRendered( node.getRight() );
            }
    	}
    }

    private double zoomToThreshold( float zoom ) {
    	return 2500.0 / Math.pow( 2, zoom );
    }

    private float thresholdToZoom( double dissimilarity ) {
    	return (float) (Math.log( 2500.0 / dissimilarity ) / Math.log( 2 )); 
    }
    
    private void slideOutChildren( DendrogramNode node, DendrogramNode parentNode ) {
    	if ( node == null ) {
    		return;
    	}
    	// Is this node visible?
    	// If yes, it will have no more visible children, by definition.
    	if ( node.getMinZoomRendered() <= zoom  &&  zoom < node.getMaxZoomRendered() ) {
    		// Yes, slide it
    		ClusterMarker cm = node.getClusterMarker();
    		if ( cm != null ) {
        		cm.splitClusterPosition = parentNode.getLatLng();
        		cm.mergeNode = null;
        		renderedNodes.add( node );				
        		refresh(cm);				
    		}
    		else {
    			cm = new ClusterMarker( factory, this, node );    		
    			addToCluster(cm, node);	    				    				
    			node.setClusterMarker( cm );    			
    			cm.splitClusterPosition = parentNode.getLatLng();
    			cm.mergeNode = null;
    			renderedNodes.add( node );				
    			refresh(cm);
    		}
    		
    		Log.e("e","Sliding out a child!");
    		
    		return;
    	}
    	slideOutChildren( node.getLeft(),  parentNode );
    	slideOutChildren( node.getRight(), parentNode );
    }
    private void slideInToMerge( DendrogramNode node, MergeNode targetNode ) {
    	ClusterMarker cm = node.getClusterMarker();
    	if ( cm != null ) {
    		cm.mergeNode = targetNode;
    		cm.splitClusterPosition = null;
    		Log.e("e","Sliding in smaller cluster to position " + targetNode.getPosition() );
    		refresh(cm);
    	}
    }
	
    // After a zoom change, traverse the dendrogram and assign markers to new clusters. The dendrogram is not modified.
    private void evaluateDendrogramOnZoomChange( DendrogramNode node ) {
    	if ( node == null ) {
    	}
    	else
    	{
    		// This node could be pending render after a merger, but at new zoom may not be visible any more.... 
    		if ( pendingRenderNodes.contains( node ) ) {
    			// Cancel a merge?
    		}
    		
    		// Is the node still visible at the current(new) zoom level?
    		if ( node.getMinZoomRendered() <= zoom  &&  zoom < node.getMaxZoomRendered() ) {
    			Log.e("e","evaluateDendrogramOnZoomChange still visible. min=" + node.getMinZoomRendered() + " max=" + node.getMaxZoomRendered() );
    			// Yes. Typically no-op.
    			// But if it's animating, cancel the animation, and animate back to it's correct state.
    			ClusterMarker cm = node.getClusterMarker();
    			if ( cm != null ) {
    				cm.animateToPlace();
    			}
    		}
    		else
    		if ( zoom >= node.getMaxZoomRendered() ) {
    			Log.e("e","evaluateDendrogramOnZoomChange nuke and split min=" + node.getMinZoomRendered() + " max=" + node.getMaxZoomRendered() );
    			// No. This node needs to be nuked immediately and split up.
    			ClusterMarker cm = node.getClusterMarker();
    			if ( cm != null ) {
    				cm.removeVirtual(); 			
    				node.setClusterMarker( null );
    			}
    			renderedNodes.remove( node );
    			
    			// Slide out it's children
    			slideOutChildren( node.getLeft(), node );
    			slideOutChildren( node.getRight(), node );
    		}
    		else
    		if ( zoom < node.getMinZoomRendered() ) {
    			Log.e("e","evaluateDendrogramOnZoomChange merge with parent min=" + node.getMinZoomRendered() + " max=" + node.getMaxZoomRendered() );
    			// No. This node needs to be merged with it's new parent. What is the new parent?
    			MergeNode newparent = node.getParent();
    			while ( zoom < newparent.getMinZoomRendered() ) {
    				newparent = newparent.getParent();
    			}
    			Log.e("e","new parent=" + newparent + " parentmin=" + newparent.getMinZoomRendered() + " parentmax=" + newparent.getMaxZoomRendered() );
    			pendingRenderNodes.add( newparent );
    			slideInToMerge( node, newparent );
    		}    			
    	}
    }
    
    public HierarchicalClusteringStrategy( ClusteringSettings settings, DelegatingGoogleMap factory, List<DelegatingMarker> fullMarkerList, ClusterRefresher refresher ) {
    	this.fullMarkerList = fullMarkerList;
        this.clusterOptionsProvider = settings.getClusterOptionsProvider();
        this.factory = factory;
        this.markers = new HashMap<DelegatingMarker, ClusterMarker>();
        this.refresher = refresher;
        this.zoom = factory.real.getCameraPosition().zoom;
        
        reComputeDendrogram();
    }
    
    private void cleanAllClusters( DendrogramNode node ) {
    	if ( node == null ) {
    		return;
    	}
    	ClusterMarker cm = node.getClusterMarker();
    	if ( cm != null ) {
    		cm.removeVirtual();
    	}
    	node.setClusterMarker( null );
    	cleanAllClusters( node.getLeft() );
    	cleanAllClusters( node.getRight() );
    }
    @Override
    public void cleanup() {
    	if ( dendrogram != null ) {
    		cleanAllClusters( dendrogram.getRoot() );
    	}
    	if ( markers != null ) {
    		markers.clear();	
    	}
        if ( refresher != null ) {
        	refresher.cleanup();
        }
    }

    @Override
    public void onCameraChange( CameraPosition cameraPosition ) {
    	Log.e("e","CameraChange");
        oldZoom = zoom;
        zoom = cameraPosition.zoom;               
        
        VisibleRegion visibleRegion = factory.real.getVisibleRegion();
        LatLngBounds bounds = visibleRegion.latLngBounds;
        
        // First, nuke any markers no longer visible (if we zoomed in or panned)        
        removeClustersNowNotInVisibleRegion();
        
        if ( zoomedIn()  ||  zoomedOut() ) {
        	Log.e("e","Zoom changed from " + oldZoom + " to " + zoom);
        	
        	// First, clusterify without animation 
            clusterify(false);
            
        	// TODO - Are there any pending animations? If yes, cancel them.
        	
        	List<DendrogramNode> renderedNodesList = new ArrayList<DendrogramNode>( renderedNodes ); // to avoid concurrent modification exception
        	for ( DendrogramNode node : renderedNodesList ) {
        		Log.e("e","Zoom change, evaluating node from rendered Nodes List " + node);
        		evaluateDendrogramOnZoomChange( node );
        	}
        }
        
        // Last, if we panned or zoomed out, add any new clusters (without animation)
        addClustersNowInVisibleRegion();
        refresher.refreshAll();
    }
    
    // This is used when e.g. a cluster is declusterified by user
    @Override
    public void onClusterGroupChange( DelegatingMarker marker ) {
        if ( ! marker.isVisible() ) {
            return;
        }
        ClusterMarker oldCluster = markers.get( marker );
        if ( oldCluster != null ) {
            oldCluster.remove( marker );
            refresh(oldCluster);
        }
        
        int clusterGroup = marker.getClusterGroup();
        if ( clusterGroup < 0 ) {
            markers.put(marker, null);
            if ( factory.real.getCameraPosition().zoom >= marker.getMinZoomLevelVisible() ) {
            	marker.changeVisible(true);
            }
            else {
            	marker.changeVisible(false);
            }
        } else {
        	marker.changeVisible(false);
        	reComputeDendrogram();
        }
    }
    
    @Override
    public void onAdd( DelegatingMarker marker ) {
        if ( ! marker.isVisible() ) {
            return;
        }
        addMarker(marker);
    }
    
    @Override
    public void onBulkAdd( List<DelegatingMarker> marker ) {
    	Log.e("e","Hierarchical onBulkAdd");
    	for ( DelegatingMarker m : marker ) {
    		if ( m.isVisible() ) {    	
    			fullMarkerList.add( m );
    		}
    	}
        reComputeDendrogram();
    }
    
    private void addMarker( DelegatingMarker marker ) {
    	fullMarkerList.add( marker );
    	
    	// Recalculate everything
    	reComputeDendrogram();
    }
    
    /*
    private boolean isPositionInVisibleClusters(LatLng position) {
        int y = convLat(position.latitude);
        int x = convLng(position.longitude);
        int[] b = visibleClusters;
        return b[0] <= y && y <= b[2] && (b[1] <= x && x <= b[3] || b[1] > b[3] && (b[1] <= x || x <= b[3]));
    }
*/
    @Override
    public void onRemove( DelegatingMarker marker ) {
        if ( ! marker.isVisible() ) {
            return;
        }
        removeMarker( marker );
    }

    private void removeMarker( DelegatingMarker marker ) {
    	fullMarkerList.remove( marker );
    	// Recalculate everything
    	reComputeDendrogram();
    }
    
    @Override
    public void onPositionChange( DelegatingMarker marker ) {
        if ( ! marker.isVisible() ) {
            return;
        }
    	// Recalculate everything
    	reComputeDendrogram();
    }
    
    private ClusterMarker findOriginal( DendrogramNode node, com.google.android.gms.maps.model.Marker original, ClusterMarker ret ) {
    	if ( node != null  &&  node.getClusterMarker() != null  &&  original.equals( node.getClusterMarker().getVirtual() ) ) {
    		Log.e("e","findOriginal found!");
    		ret = node.getClusterMarker();
    		return ret;
    	}
    	if ( node instanceof MergeNode ) {
    		if ( ret == null ) {
    			ret = findOriginal( node.getLeft(),  original, ret );
    		}
    		if ( ret == null ) {
    			ret = findOriginal( node.getRight(), original, ret );
    		}
    	}
    	return ret;
    }
    @Override
    public Marker map( com.google.android.gms.maps.model.Marker original ) {
    	ClusterMarker ret = findOriginal( dendrogram.getRoot(), original, null );
    	
    	Log.e("e","hierarchical marker click, ret=" + ret);
    	return ret;
    }
    
    private void getDisplayedMarkers( DendrogramNode node, List<Marker> displayedMarkers ) {
    	if ( node != null ) {
    		ClusterMarker cm = node.getClusterMarker();
    		if ( cm != null ) {
    			Marker displayedMarker = cm.getDisplayedMarker();
    			if ( displayedMarker != null ) {
    				displayedMarkers.add( displayedMarker );
    			}
    		}
    		if ( node instanceof MergeNode ) {
    			getDisplayedMarkers( node.getLeft(),  displayedMarkers );
    			getDisplayedMarkers( node.getRight(), displayedMarkers );
    		}
    	}
    }
    @Override
    public List<Marker> getDisplayedMarkers() {
        List<Marker> displayedMarkers = new ArrayList<Marker>();
        getDisplayedMarkers( dendrogram.getRoot(), displayedMarkers );
        return displayedMarkers;
    }
    
    @Override
    public float getMinZoomLevelNotClustered( Marker marker ) {
        if ( ! fullMarkerList.contains( marker) ) {
            throw new UnsupportedOperationException( "marker is not visible or is a cluster" );
        }
        DelegatingMarker dm = (DelegatingMarker)marker;
        double dissimilarity = dm.parentNode.getDissimilarity();
              
        float zoom = 25;
        while ( zoom >= 0 ) {
        	double threshold = zoomToThreshold(zoom);
        	if ( dissimilarity < threshold ) {
        		break;
        	}
            zoom -= 1;
        }
        
        return zoom;
    }
    
    // Find the parent node of the marker in the dendrogram (which will be a MergeNode, if it has one), then get the 
    // dissimilarity measure and compare to threshold.
    
    @Override
    public void onVisibilityChangeRequest(DelegatingMarker marker, boolean visible) {
    	Log.e("E","onVisChangeReq");
        if ( visible ) {
            addMarker(marker);
        } else {
            removeMarker(marker);
            marker.changeVisible(false);
        }
    }

    @Override
    public void onShowInfoWindow( DelegatingMarker marker ) {
        if ( ! marker.isVisible() ) {
            return;
        }
        ClusterMarker cluster = markers.get(marker);
        if ( cluster == null ) {
            marker.forceShowInfoWindow();
        } 
        else 
        if ( cluster.getMarkersInternal().size() == 1 ) {
            refresh(cluster);
            marker.forceShowInfoWindow();
        }
    }
    
    private void refresh( ClusterMarker cluster ) {
        if ( cluster != null ) {
            refresher.refresh( cluster );
        }
    }

    private boolean zoomedIn() {
        return zoom > oldZoom;
    }
    private boolean zoomedOut() {
        return zoom < oldZoom;
    }
    
    private void addVisibleClusters( DendrogramNode node, LatLngBounds bounds ) {
    	if ( node != null ) {
    		if ( ! renderedNodes.contains( node )  &&  ! pendingRenderNodes.contains( node ) ) {
    			if ( bounds.contains( node.getLatLng() ) ) {
    				if ( node.getMinZoomRendered() <= zoom  &&  zoom < node.getMaxZoomRendered() ) {
    					if ( node.getClusterMarker() == null ) {
    						// Draw the cluster
    						ClusterMarker cm = new ClusterMarker( factory, this, node );
    						addToCluster(cm, node);
    						cm.splitClusterPosition = null; // Not animating
    						cm.mergeNode = null;    						
    						node.setClusterMarker( cm );
    						Log.e("e","addVisibleClusters: Adding visible cluster marker with size " + cm.getMarkersInternal().size() + " node" + node + " has cluster " + node.getClusterMarker() + " min=" + node.getMinZoomRendered() + " max=" + node.getMaxZoomRendered() + " zoom=" + zoom);
    						refresh(cm);
    						renderedNodes.add( node );
    					}
    				}
    			}
    		}
    		if ( node instanceof MergeNode ) {
    			addVisibleClusters( node.getLeft(), bounds );
    			addVisibleClusters( node.getRight(), bounds );
    		}
    	}
    }
    // Add single markers and clusters in visible region, upon a pan or zoom out for example
    /*
    We should have a map of all displayed markers, for quick switching off.
    Also, we should make use of kdtree (markers+clusters, constructed during initialization) for efficient switching on.
    No need to test for visible region of markers we know are too far off screen.
    */
    // Also, optimize deletion from tree
    private void removeClustersNowNotInVisibleRegion() {
    	Log.e("e","Remove clusters not in Visible Region");
    	VisibleRegion visibleRegion = factory.real.getVisibleRegion();
    	LatLngBounds bounds = visibleRegion.latLngBounds;
    	Iterator<DendrogramNode> it = renderedNodes.iterator();
    	while ( it.hasNext() ) {
    		DendrogramNode node = it.next();
    		if ( ! bounds.contains( node.getLatLng() ) ) {
    			ClusterMarker cm = node.getClusterMarker();
    			if ( cm != null ) {
    				Log.e("e","Remove clusters not in Visible Region, REMOVING " + cm);    				
    				cm.changeVisible( false );
    				node.setClusterMarker( null );
    			}
				it.remove();
    		}
    	}
    }
    
    private void addClustersNowInVisibleRegion() {
    	Log.e("e","Show clusters in Visible Region");
    	// Do we need to add any new clusters? No split/merge animation will happen here.
    	// We can pre-compute at dendrogram construction time the zoom range at which each point will be rendered
    	// Start at the root of the tr
    	//List<DendrogramNode> visibleNodes = tree.getRange( low, high );
    	
    	VisibleRegion visibleRegion = factory.real.getVisibleRegion();
    	LatLngBounds bounds = visibleRegion.latLngBounds;
		addVisibleClusters( dendrogram.getRoot(), bounds );
        refresher.refreshAll();
    }
    
    boolean isVisible( LatLng pos ) {
    	 VisibleRegion visibleRegion = factory.real.getVisibleRegion();
         LatLngBounds bounds = visibleRegion.latLngBounds;
         if ( factory.getCameraPosition().zoom <= 2 ) 
        	 return true;
         if ( bounds.contains( pos ) ) // This breaks for low zoom levels when the camera view contains 180deg meridian
        	 return true;
         else
        	 return false;
    }
    
    /*
    private void calculateVisibleClusters() {
        
        VisibleRegion visibleRegion = map.getVisibleRegion();
        LatLngBounds bounds = visibleRegion.latLngBounds;
        visibleClusters[0] = convLat(bounds.southwest.latitude);
        visibleClusters[1] = convLng(bounds.southwest.longitude);
        visibleClusters[2] = convLat(bounds.northeast.latitude);
        visibleClusters[3] = convLng(bounds.northeast.longitude);
    }
    */
    /*
    private ClusterKey calculateClusterKey(int group, LatLng position) {
        int y = convLat(position.latitude);
        int x = convLng(position.longitude);
        return new ClusterKey(group, y, x);
    }
    */
/*
    private int convLat(double lat) {
        return (int) (SphericalMercator.scaleLatitude(lat) / clusterSize);
    }

    private int convLng(double lng) {
        return (int) (SphericalMercator.scaleLongitude(lng) / clusterSize);
    }
*/
    /*
    private double calculateClusterSize(int zoom) {
        return baseClusterSize / (1 << zoom);
    }
    */
    
    
    com.google.android.gms.maps.model.Marker createClusterMarker(List<Marker> markers, LatLng position) {
        markerOptions.position(position);
        ClusterOptions opts = clusterOptionsProvider.getClusterOptions( markers );
        markerOptions.icon( opts.getIcon() );
        if ( GOOGLE_PLAY_SERVICES_4_0 ) {
            try {
                markerOptions.alpha( opts.getAlpha() );
            } catch ( NoSuchMethodError error ) {
                // not the cutest way to handle backward compatibility
                GOOGLE_PLAY_SERVICES_4_0 = false;
            }
        }
        markerOptions.anchor( opts.getAnchorU(), opts.getAnchorV() );
        markerOptions.flat(opts.isFlat());
        markerOptions.infoWindowAnchor( opts.getInfoWindowAnchorU(), opts.getInfoWindowAnchorV() );
        markerOptions.rotation( opts.getRotation() );
        return factory.real.addMarker( markerOptions );
    }
    
	@Override
	public void refreshAll() {
		refresher.refreshAll();		
	}
	
	private Queue<ClusterMarker> mDeclusterifiedClusters = new LinkedList<ClusterMarker>();
	private double calculateDistanceBetweenMarkers() {
		IProjection projection = factory.real.getProjection();
		android.graphics.Point point = projection.toScreenLocation( new LatLng(0.0, 0.0) );

		int pitch = (int)(31*factory.density);  
		point.x += pitch;
		LatLng position = projection.fromScreenLocation( point );
		return position.longitude;
	}
	@Override
	public void declusterify( Marker marker ) {
		
		ClusterMarker cm = (ClusterMarker) marker;
		cm.mergeNode = null;
		cm.splitClusterPosition = null;
		
		mDeclusterifiedClusters.add( cm );
		
		LatLng clusterPosition = cm.getPosition();
		int size = cm.getMarkers().size();
		cm.removeVirtual();
		
		double distance = calculateDistanceBetweenMarkers();			
		double currentDistance = -size / 2 * distance;
		if ( size % 2 == 0 ) { 
			currentDistance += distance/2;
		}
		for ( DelegatingMarker dm : cm.getMarkersInternal() ) {
			dm.real.setPosition( clusterPosition );
			dm.changeVisible( true );
			// TODO - bring the declusterified markers to the front
			LatLng newPosition = new LatLng( clusterPosition.latitude, clusterPosition.longitude + currentDistance );
			dm.animateScreenPosition( clusterPosition, newPosition, new AnimationSettings().interpolator( new DecelerateInterpolator() ), null );
			currentDistance += distance;
		}
	}
	@Override
	public void clusterify( boolean animate ) {
		final ClusterMarker cm = mDeclusterifiedClusters.poll();
		if ( cm != null ) {
			cm.mergeNode = null;
			cm.splitClusterPosition = null;
			
			for ( final DelegatingMarker dm : cm.getMarkersInternal() ) {
				if ( animate ) {
					dm.animateScreenPosition( dm.real.getPosition(), cm.getPosition(), new AnimationSettings().interpolator( new AccelerateInterpolator() ), new AnimationCallback() {
						@Override
						public void onFinish( Marker marker ) {
							dm.changeVisible( false );
							if ( cm != null ) {
								refresh( cm );
							}
						}
						@Override
						public void onCancel( Marker marker, CancelReason reason ) {					
						}
					} );
				} 
				else {
					dm.changeVisible( false );
					cm.refresh();
				}
			}
		}
	}
}
