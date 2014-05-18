/*
 * Copyright (C) 2013 Maciej Górski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.androidmapsextensions.impl;

import android.os.SystemClock;
import android.util.Log;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.androidmapsextensions.AnimationSettings;
import com.androidmapsextensions.Marker;
import com.androidmapsextensions.dendrogram.DendrogramNode;
import com.androidmapsextensions.dendrogram.MergeNode;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

public class ClusterMarker implements Marker {
	
	public boolean isShowing() {
		int count = markers.size();
		if ( count == 0 ) {
			return false;
		}
		else
		if ( count == 1 ) {
			DelegatingMarker dm = markers.get(0);
			if ( dm.real.isVisible() ) {
				return true;
			}
			else {
				return false;
			}
		}
		else {
			if ( virtual != null ) {
				return true;
			} 
			else {
				return false;
			}
		}
	}
    private int lastCount = -1;

    private HierarchicalClusteringStrategy strategy;
    private DendrogramNode dendrogramNode;
    LatLng splitClusterPosition; // Upon split, markers original pre-split position, used for animation
   	MergeNode mergeNode;         // Upon merge, position of parent cluster this cluster merged into, used for animation
    
    private com.google.android.gms.maps.model.Marker virtual;
    
    private List<DelegatingMarker> markers = new ArrayList<DelegatingMarker>();
    
    private DelegatingGoogleMap factory;
    public ClusterMarker( DelegatingGoogleMap factory, HierarchicalClusteringStrategy strategy, DendrogramNode node ) {
    	this.factory = factory;
        this.strategy = strategy;
        this.dendrogramNode = node;
    }
    
    com.google.android.gms.maps.model.Marker getVirtual() {
        return virtual;
    }
    
    void add(DelegatingMarker marker) {
        markers.add(marker);
    }
    
    void remove(DelegatingMarker marker) {
        markers.remove(marker);
    }
    
    void animateToPlace() {
    	Log.e("e","animateToPlace " + this);
    	int count = markers.size();
    	if ( count == 1 ) {
    		removeVirtual();
    		DelegatingMarker dm = markers.get(0);
        	if ( dm.real.getPosition() != dendrogramNode.getLatLng() ) {
        		dm.animateScreenPosition( dm.real.getPosition(), dendrogramNode.getLatLng(), new AnimationSettings().interpolator( new DecelerateInterpolator() ), new AnimationCallback() {
    				@Override
    				public void onFinish( Marker marker ) {
					}
    				@Override
    				public void onCancel( Marker marker, CancelReason reason ) {
    				}
    			} );
        	}
    	}
    	if ( count >= 2 ) {
    		if ( virtual == null ) {
    			virtual = strategy.createClusterMarker( new ArrayList<Marker>(markers), dendrogramNode.getLatLng() );
    			//splitClusterPosition = null; // Not animating
				//mergeNode = null;
				Log.e("e","Drawing animateToPlace cluster");
    		}
    		if ( virtual.getPosition() != dendrogramNode.getLatLng() ) {
    			// It is currently animating something...
    			animateScreenPosition( virtual.getPosition(), dendrogramNode.getLatLng(), new AnimationSettings().interpolator( new DecelerateInterpolator() ), null );
    		}
    	}
    }
    
    // TODO - only animate clusters which are in visible region
    void refresh() {    	
        int count = markers.size();
        if ( count == 0 ) {
            removeVirtual();
        } 
        else 
        if ( count == 1 ) {
        	removeVirtual();
        	
        	if ( splitClusterPosition == null  &&  mergeNode == null ) {
            	DelegatingMarker dm = markers.get(0);
            	dm.changeVisible(true);
        	}
        	else
            if ( splitClusterPosition != null ) {        	
            	// VH - animate the marker splitting away
            	DelegatingMarker dm = markers.get(0);
            	dm.real.setPosition( splitClusterPosition );
            	dm.changeVisible(true);
        		
        		double lat = dm.getPosition().latitude;
        		double lon = dm.getPosition().longitude;
        		Log.e("ANIMATING MARKER SPLIT", "From" + splitClusterPosition + " to " + new LatLng(lat,lon) ); 
        		
        		dm.animateScreenPosition( splitClusterPosition, new LatLng(lat,lon), new AnimationSettings().interpolator( new DecelerateInterpolator() ), null );
        		splitClusterPosition = null;
        	}
            else
            if ( mergeNode != null ) {
            	// Slide the marker in, after animation finished hide the marker and show the new parent
            	final DelegatingMarker dm = markers.get(0);
            	dm.changeVisible(true);
        		
        		Log.e("ANIMATING MARKER MERGE", " TO " + mergeNode.getPosition() );
        		final ClusterMarker mergeClusterMarker = mergeNode.getClusterMarker();
        		
        		dm.animateScreenPosition( dm.real.getPosition(), mergeNode.getLatLng(), new AnimationSettings().interpolator( new AccelerateInterpolator() ), new AnimationCallback() {
					@Override
					public void onFinish( Marker marker ) {
						dm.changeVisible(false);
						removeVirtual();
						dendrogramNode.setClusterMarker( null );
						strategy.renderedNodes.remove( dendrogramNode );
						strategy.pendingRenderNodes.remove( mergeNode );
						// Render the mergeNode
						if ( ! strategy.renderedNodes.contains( mergeNode ) ) {						
							strategy.renderedNodes.add( mergeNode );
							
    						// Draw the cluster
    						ClusterMarker cm = new ClusterMarker( factory, strategy, mergeNode );
    						strategy.addToCluster(cm, mergeNode);
    						cm.splitClusterPosition = null; // Not animating
    						cm.mergeNode = null;    						
    						mergeNode.setClusterMarker( cm );
    						cm.refresh();
    						Log.e("e","Drawing merge cluster");
						}
					}
					@Override
					public void onCancel( Marker marker, CancelReason reason ) {
						strategy.pendingRenderNodes.remove( mergeNode );
						/*
						dm.changeVisible(false);
						removeVirtual();
						if ( mergeClusterMarker != null ) {
							mergeClusterMarker.removeVirtual();
						}
						mergeNode.setClusterMarker( null );
						*/						
					}
				} );        		
            }
        } else { // Real Cluster with 2 or more items
        	if ( mergeNode != null ) {
        		Log.e("e","Merging real cluster with 2 or more markers " + markers.size() );
        		
        		final ClusterMarker mergeClusterMarker = mergeNode.getClusterMarker();
        		
        		animateScreenPosition( virtual.getPosition(), new LatLng( mergeNode.getPosition()[0], mergeNode.getPosition()[1] ), new AnimationSettings().interpolator( new AccelerateInterpolator() ), new AnimationCallback() {
					@Override
					public void onFinish( Marker marker ) {
						Log.e("!!!!!!!!!!!!!!!!!!!","Finished cluster merge, mergeNode=" + mergeNode);
						removeVirtual();
						dendrogramNode.setClusterMarker( null );
						strategy.renderedNodes.remove( dendrogramNode );
						strategy.pendingRenderNodes.remove( mergeNode );
						// Render the mergeNode
						if ( ! strategy.renderedNodes.contains( mergeNode ) ) {						
							strategy.renderedNodes.add( mergeNode );
							
    						// Draw the cluster
    						ClusterMarker cm = new ClusterMarker( factory, strategy, mergeNode );
    						strategy.addToCluster(cm, mergeNode);
    						cm.splitClusterPosition = null; // Not animating
    						cm.mergeNode = null;    						
    						mergeNode.setClusterMarker( cm );
    						cm.refresh();
    						Log.e("e","Drawing merge cluster");
						}
					}
					@Override
					public void onCancel( Marker marker, CancelReason reason ) {
						strategy.pendingRenderNodes.remove( mergeNode );
						Log.e("!!!!!!!!!!!!!!!!!!!","Canceling cluster merge");
					}
				} );
        		return;
        	}
        	if ( splitClusterPosition != null ) {
        		Log.e("ANIMATING","Splitting real cluster with 2 or more markers " + markers.size() +" , removing virtual");
 
        		if ( virtual == null  ||  lastCount != count ) {
        			removeVirtual();
                    lastCount = count;
                    virtual = strategy.createClusterMarker(new ArrayList<Marker>(markers), dendrogramNode.getLatLng() );
        		}
        		animateScreenPosition( splitClusterPosition, dendrogramNode.getLatLng(), new AnimationSettings().interpolator( new DecelerateInterpolator() ), null );
        		
        		splitClusterPosition = null;
        	}
        	
            // Show new cluster marker only after animation is complete
            // TODO - need to animate cluster markers as well
            if ( virtual == null  ||  lastCount != count ) {
                removeVirtual();
                lastCount = count;
                virtual = strategy.createClusterMarker(new ArrayList<Marker>(markers), dendrogramNode.getLatLng() );
            } 
            else {
                virtual.setPosition( dendrogramNode.getLatLng() );
            }
        }
    }

    Marker getDisplayedMarker() {
        int count = markers.size();
        if ( count == 0 ) {
            return null;
        } 
        else 
        if ( count == 1 ) {
            return markers.get(0);
        } 
        else {
            return this;
        }
    }

    void removeVirtual( LatLng slideTo ) {
        if ( virtual != null ) {
        	/*
        	MarkerManager = 
            LazyMarker realMarker = new LazyMarker(factory.getMap(), markerOptions, this);
            DelegatingMarker marker = new DelegatingMarker(realMarker, this);

        	LazyMarker dummyLm = new LazyMarker(null, null);
        	
        	DelegatingMarker dummy = new DelegatingMarker(virtual, null);        	
            manager.markerAnimator.cancelAnimation( dummy, Marker.AnimationCallback.CancelReason.ANIMATE_POSITION);
            manager.markerAnimator.animateScreen(   dummy, virtual.getPosition(), slideTo, SystemClock.uptimeMillis(), new AnimationSettings(), null);
        	//virtual	.        	 
        	 */
            virtual.remove();
            virtual = null;
        }
    }

    void removeVirtual() {
        if (virtual != null) {
            virtual.remove();
            virtual = null;
        }
    }

    void cleanup() {
        if (virtual != null) {
            virtual.remove();
        }
    }

    List<DelegatingMarker> getMarkersInternal() {
        return new ArrayList<DelegatingMarker>(markers);
    }

    @Override
    public void animatePosition(LatLng target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void animatePosition(LatLng target, AnimationSettings settings) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void animatePosition(LatLng target, AnimationCallback callback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void animatePosition(LatLng target, AnimationSettings settings, AnimationCallback callback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float getAlpha() {
        if (virtual != null) {
            return virtual.getAlpha();
        }
        return 1.0f;
    }

    @Override
    public int getClusterGroup() {
        if ( markers.size() > 0 ) {
            return markers.get(0).getClusterGroup();
        }
        throw new IllegalStateException();
    }

    @Override
    public Object getData() {
        return null;
    }

    @Deprecated
    @Override
    public String getId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Marker> getMarkers() {
        return new ArrayList<Marker>(markers);
    }

    @Override
    public LatLng getPosition() {
    	return dendrogramNode.getLatLng();
    	/*
        if ( virtual != null ) {
            return virtual.getPosition();
        }
        LatLngBounds.Builder builder = LatLngBounds.builder();
        for ( DelegatingMarker m : markers ) {
            builder.include( m.getPosition() );
        }
        LatLng position = builder.build().getCenter();
        return position;
        */
    }

    @Override
    public float getRotation() {
        if (virtual != null) {
            return virtual.getRotation();
        }
        return 0.0f;
    }

    @Override
    public String getSnippet() {
        return null;
    }

    @Override
    public String getTitle() {
        return null;
    }

    @Override
    public void hideInfoWindow() {
        if (virtual != null) {
            virtual.hideInfoWindow();
        }
    }

    @Override
    public boolean isCluster() {
        return true;
    }

    @Override
    public boolean isDraggable() {
        return false;
    }

    @Override
    public boolean isFlat() {
        if (virtual != null) {
            return virtual.isFlat();
        }
        return false;
    }

    @Override
    public boolean isInfoWindowShown() {
        if ( virtual != null ) {
            return virtual.isInfoWindowShown();
        }
        return false;
    }

    @Override
    public boolean isVisible() {
        if ( virtual != null ) {
            return virtual.isVisible();
        }
        return false;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAlpha(float alpha) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAnchor(float anchorU, float anchorV) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setClusterGroup( int clusterGroup ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setData(Object data) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDraggable(boolean draggable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFlat(boolean flat) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setIcon(BitmapDescriptor icon) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setInfoWindowAnchor(float anchorU, float anchorV) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPosition(LatLng position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRotation(float rotation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSnippet(String snippet) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTitle(String title) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setVisible(boolean visible) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void showInfoWindow() {
        if ( virtual == null  &&  markers.size() > 1 ) {
            refresh();
        }
        if ( virtual != null ) {
            virtual.showInfoWindow();
        }
    }

    void setVirtualPosition( LatLng position ) {
        int count = markers.size();
        if ( count == 0 ) {
            // no op
        } 
        else 
        if ( count == 1 ) {
            markers.get(0).setVirtualPosition( position );
        } else {
            virtual.setPosition( position );
        }
    }

    @Override
	public void animateScreenPosition( LatLng from, LatLng to, AnimationSettings animsettings, AnimationCallback callback ) {
        if ( from == null  ||  to == null ) {
            throw new IllegalArgumentException();
        }
        // Is it animating? If yes, cancel the animation, and start new animation from current position
        //if ( virtual != null  &&  from != virtual.getPosition() ) { 
        //	from = virtual.getPosition();
        //}
        factory.markerAnimator.cancelScreenAnimation( this, Marker.AnimationCallback.CancelReason.ANIMATE_POSITION );
        factory.markerAnimator.animateScreen( this, from, to, SystemClock.uptimeMillis(), animsettings, callback );
	}

	public void changeVisible( boolean visible ) {
		Log.e("e","Cluster ChangeVisible " + this + " to " + visible );
		if ( ! visible ) {
			removeVirtual();		
			int count = markers.size();	        
	        if ( count == 1 ) {
	        	DelegatingMarker dm = markers.get(0);
	        	dm.changeVisible(false);
	        }
		}
		else {
			if ( virtual == null  &&  markers.size() > 1 ) {
				virtual = strategy.createClusterMarker( new ArrayList<Marker>(markers), dendrogramNode.getLatLng() );
			}
			else
			if ( markers.size() == 1 ) {
				//removeVirtual(); // TODO - not needed?
	        	DelegatingMarker dm = markers.get(0);
	        	dm.changeVisible(true);
			}
		}
	}
	
	@Override
	public void setPositionDuringScreenAnimation( LatLng position ) {
		if ( virtual != null ) { // TODO - this should not be necessary
			virtual.setPosition( position );
		}
	}
}