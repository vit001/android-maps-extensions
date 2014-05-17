package ags.utils.dataStructures.trees.thirdGenKD;

import ags.utils.dataStructures.MaxHeap;

public class Test {
	public static void main( String[] args ) {
		KdTree<String> tree = new KdTree<String>(2);
		tree.addPoint( new double[]{1,2}, "A1" );
		tree.addPoint( new double[]{2,1}, "B1" );
		tree.addPoint( new double[]{2,2}, "C1" );

		double[] searchPoint = new double[]{1,1};
		MaxHeap<String> res = tree.findNearestNeighbors( searchPoint, 1, new SquareEuclideanDistanceFunction() );
		
		System.out.println("done size=" + res.size() );
	}		
}
