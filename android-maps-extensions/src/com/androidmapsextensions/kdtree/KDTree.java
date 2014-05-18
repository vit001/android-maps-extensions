/* 
 * Based on Chase's KDTree-C 
 * Copyright (c) 2012 Chase
 *
 * This software is provided 'as-is', without any express or implied
 * warranty. In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 * 1. The origin of this software must not be misrepresented; you must not
 * claim that you wrote the original software. If you use this software
 * in a product, an acknowledgment in the product documentation would be
 * appreciated but is not required.
 * 
 * 2. Altered source versions must be plainly marked as such, and must not be
 * misrepresented as being the original software.
 *
 * 3. This notice may not be removed or altered from any source
 * distribution.
 */
package com.androidmapsextensions.kdtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
 
/**
 * This is a KD Bucket Tree, for fast sorting and searching of K dimensional
 * data.
 * 
 * @author Chase
 * 
 */
public class KDTree<T> {
	protected static final int defaultBucketSize = 48;
 
	private final int dimensions;
	private final int bucketSize;
	private NodeKD root;
 
	/**
	 * Constructor with value for dimensions.
	 * 
	 * @param dims - Number of dimensions
	 */
	public KDTree( int dims ) {
		this.dimensions = dims;
		this.bucketSize = defaultBucketSize;
		this.root = new NodeKD();
	}
 
	/**
	 * Constructor with value for dimensions and bucket size.
	 * 
	 * @param dims
	 *            - Number of dimensions
	 * @param bucket
	 *            - Size of the buckets.
	 */
	public KDTree( int dims, int bucket ) {
		this.dimensions = dims;
		this.bucketSize = bucket;
		this.root = new NodeKD();
	}
 
	/**
	 * Add a key and its associated value to the tree.
	 * 
	 * @param key
	 *            - Key to add
	 * @param val
	 *            - object to add
	 */
	public void add( double[] key, T val ) {
		root.addPoint( key, val );
	}
	
	/**
	 * Removes a key and its associated value from the tree. No rebalancing is performed.
	 * 
	 * @param key
	 *            - Key to remove
	 * @param val
	 *            - object to remove
	 */
	public void delete( double[] key, T val ) {
		root.deletePoint( key, val );
	}
	 
	/**
	 * Returns all PointKD within a certain range defined by an upper and lower
	 * PointKD.
	 * 
	 * @param low
	 *            - lower bounds of area
	 * @param high
	 *            - upper bounds of area
	 * @return - All PointKD between low and high.
	 */
	@SuppressWarnings("unchecked")
	public List<T> getRange( double[] low, double[] high ) {
		Object[] objs = root.range( high, low );
		ArrayList<T> range = new ArrayList<T>( objs.length );
		for( int i = 0; i < objs.length; ++i ) {
			range.add( (T)objs[i] );
		}
		return range;
	}
 
	/**
	 * Gets the N nearest neighbors to the given key.
	 * 
	 * @param key
	 *            - Key
	 * @param num
	 *            - Number of results
	 * @return Array of Item Objects, distances within the items are the square
	 *         of the actual distance between them and the key
	 */
	public ResultHeap<T> getNearestNeighbors(double[] key, int num) {
		ResultHeap<T> heap = new ResultHeap<T>(num);
		root.nearest(heap, key);
		return heap;
	}
 
 
	// Internal tree node
	private class NodeKD {
		private NodeKD left, right;
		private double[] maxBounds, minBounds;
		private Object[] bucketValues;
		private double[][] bucketKeys;
		private boolean[] isDeleted;
		private boolean isLeaf;
		private int current, sliceDimension;
		private double slice;
 
		private NodeKD() {
			bucketValues = new Object[bucketSize];
			bucketKeys = new double[bucketSize][];
			isDeleted = new boolean[bucketSize];
			Arrays.fill( isDeleted, false );
			
			left = right = null;
			maxBounds = minBounds = null;
			
			isLeaf = true;
			
			current = 0;
		}
		
		// What it says on the tin
		private void addPoint(double[] key, Object val) {
			if( isLeaf ) {
				addLeafPoint( key,val );
			} else {
				extendBounds( key );
				if ( key[sliceDimension] > slice ) {
					right.addPoint( key, val );
				} else {
					left.addPoint( key, val );
				}
			}
		}
		
		// What it says on the tin
		private void deletePoint( double[] key, Object val ) {
			if( isLeaf ) {
				deleteLeafPoint( key, val );
			} else {
				if ( key[sliceDimension] > slice ) {
					right.deletePoint(key, val);
				} else {
					left.deletePoint(key, val);
				}
			}
		}
		
		private void addLeafPoint( double[] key, Object val ) {
			extendBounds( key );
			if ( current + 1 > bucketSize ) {
				splitLeaf();
				addPoint( key, val );
				return;
			}
			bucketKeys[current] = key;
			bucketValues[current] = val;
			++current;
		}
		
		private void deleteLeafPoint( double[] key, Object val ) {
			for ( int i = 0; i < current; ++i ) {
				if ( isDeleted[i] == false  &&  Arrays.equals( bucketKeys[i], key )  &&  bucketValues[i].equals( val ) ) {
					isDeleted[i] = true;
					bucketValues[i] = null;
				}
			}
		}
		
		/**
		 * Find the nearest neighbor recursively.
		 */
		@SuppressWarnings("unchecked")
		private void nearest( ResultHeap<T> heap, double[] data ) {
			if ( current == 0 )
				return;
			if ( isLeaf ) {
				//IS LEAF
				for ( int i = 0; i < current; ++i ) {
					if ( isDeleted[i] ) {
						continue;
					}
					double dist = pointDistSq( bucketKeys[i], data );
					heap.offer( dist, (T) bucketValues[i] );
				}
			} 
			else {
				//IS BRANCH
				if ( data[sliceDimension] > slice ) {
					right.nearest( heap, data );
					if ( left.current == 0 )
						return;
					if ( ! heap.isFull()  ||  regionDistSq(data,left.minBounds,left.maxBounds) < heap.getMaxKey() ) {
						left.nearest( heap, data );
					}
				} 
				else {
					left.nearest( heap, data );
					if ( right.current == 0 )
						return;
					if ( ! heap.isFull()  ||  regionDistSq(data,right.minBounds,right.maxBounds) < heap.getMaxKey() ) {
						right.nearest( heap, data );
					}
				}
			}
		}
		
		// Gets all items from within a range
		private Object[] range( double[] upper, double[] lower ) {
			if ( bucketValues == null ) {
				// Branch
				Object[] tmp = new Object[0];
				if ( intersects(upper, lower, left.maxBounds, left.minBounds) ) {
					Object[] tmpl = left.range(upper, lower);
					if (0 == tmp.length) tmp = tmpl;
				}
				if ( intersects(upper, lower, right.maxBounds, right.minBounds) ) {
					Object[] tmpr = right.range(upper, lower);
					if ( 0 == tmp.length )
						tmp = tmpr;
					else 
					if ( 0 < tmpr.length ) {
						Object[] tmp2 = new Object[tmp.length + tmpr.length];
						System.arraycopy(tmp, 0, tmp2, 0, tmp.length);
						System.arraycopy(tmpr, 0, tmp2, tmp.length, tmpr.length);
						tmp = tmp2;
					}
				}
				return tmp;
			}
			// Leaf
			Object[] tmp = new Object[current];
			int n = 0;
			for( int i = 0; i < current; ++i ) {
				if ( ! isDeleted[i]  &&  contains(upper, lower, bucketKeys[i]) ) {
					tmp[n++] = bucketValues[i];
				}
			}
			Object[] tmp2 = new Object[n];
			System.arraycopy( tmp, 0, tmp2, 0, n );
			return tmp2;
		}
 
		// These are helper functions from here down
		// Check if this hyper rectangle contains a give hyper-point
		public boolean contains( double[] upper, double[] lower, double[] point ) {
			if ( current == 0 ) return false;
			for ( int i = 0; i < point.length; ++i ) {
				if ( point[i] > upper[i]  ||  point[i] < lower[i] ) return false;
			}
			return true;
		}
 
		// Checks if two hyper-rectangles intersect
		public boolean intersects( double[] up0, double[] low0, double[] up1, double[] low1 ) {
			for ( int i = 0; i < up0.length; ++i ) {
				if ( up1[i] < low0[i]  ||  low1[i] > up0[i] ) return false;
			}
			return true;
		}

		// VH - Ignore any previously deleted points 
		private void splitLeaf() {
			double bestRange = 0;
			for ( int i = 0; i < dimensions; ++i ) {
				double range = maxBounds[i] - minBounds[i];
				if ( range > bestRange ) {
					sliceDimension = i;
					bestRange = range;
				}
			}
			
			left  = new NodeKD();
			right = new NodeKD();
			
			slice = ( maxBounds[sliceDimension] + minBounds[sliceDimension] ) * 0.5;
			
			for ( int i = 0; i < current; ++i ) {
				if ( isDeleted[i] ) {
					continue;
				}
				if ( bucketKeys[i][sliceDimension] > slice ) {
					right.addLeafPoint( bucketKeys[i], bucketValues[i] );
				} 
				else {
					left.addLeafPoint( bucketKeys[i], bucketValues[i] );
				}
			}
			bucketKeys   = null;
			bucketValues = null;
			isDeleted    = null;
			isLeaf = false;
		}
		
		// Expands this hyper rectangle
		private void extendBounds( double[] key ) {
			if ( maxBounds == null ) {
				maxBounds = Arrays.copyOf( key, dimensions );
				minBounds = Arrays.copyOf( key, dimensions );
				return;
			}
			for ( int i = 0; i < key.length; ++i ) {
				if ( maxBounds[i] < key[i] ) maxBounds[i] = key[i];
				if ( minBounds[i] > key[i] ) minBounds[i] = key[i];
			}
		}
	}
 
	/* I may have borrowed these from an early version of Red's tree. I however forget. */
	private static final double pointDistSq(double[] p1, double[] p2) {
        double d = 0;
        double q = 0;
        for (int i = 0; i < p1.length; ++i) {
            d += (q=(p1[i] - p2[i]))*q;
        }
        return d;
    }
 
    private static final double regionDistSq( double[] point, double[] min, double[] max ) {
        double d = 0;
        double q = 0;
        for ( int i = 0; i < point.length; ++i ) {
            if ( point[i] > max[i] ) {
            	d += (q = (point[i] - max[i]))*q;
            } 
            else 
            if ( point[i] < min[i] ) {
                d += (q = (point[i] - min[i]))*q;
            }
        }
        return d;
    }
}