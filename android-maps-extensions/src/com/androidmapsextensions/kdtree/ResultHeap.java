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

/**
 * @author Chase
 * 
 * @param <T>
 */
public class ResultHeap<T> {
	private Object[] data;
	private double[] keys;
	private int capacity;
	private int size;
 
	protected ResultHeap(int capacity) {
		this.data = new Object[capacity];
		this.keys = new double[capacity];
		this.capacity = capacity;
		this.size = 0;
	}
 
	protected void offer( double key, T value ) {
		int i = size;
		for (; i > 0 && keys[i - 1] > key; --i);
		if ( i >= capacity ) return;
		if ( size < capacity ) ++size;
		int j = i + 1;
		System.arraycopy(keys, i, keys, j, size - j);
		keys[i] = key;
		System.arraycopy(data, i, data, j, size - j);
		data[i] = value;
	}
 
	public double getMaxKey() {
		return keys[size - 1];
	}
 
	@SuppressWarnings("unchecked")
	public T removeMax() {
		if(isEmpty()) return null;
		return (T)data[--size];
	}
 
	public boolean isEmpty() {
		return size == 0;
	}
 
	public boolean isFull() {
		return size == capacity;
	}
 
	public int size() {
		return size;
	}
 
	public int capacity() {
		return capacity;
	}
}