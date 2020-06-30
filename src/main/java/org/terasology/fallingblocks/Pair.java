// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks;

public class Pair<A,B>{
    public final A a;
    public final B b;
    
    public Pair(A a, B b) {
        this.a = a;
        this.b = b;
    }
    
    public int hashCode() {
        return (a == null ? 0 : a.hashCode()) + (b == null ? 0 : 269549569*b.hashCode());
    }
    
    public boolean equals(Object other) {
        if(other == null || other.getClass() != Pair.class) {
            return false;
        } else {
            Pair that = (Pair) other;
            return (this.a == null && that.a == null || this.a.equals(that.a))
                && (this.b == null && that.b == null || this.b.equals(that.b));
        }
    }
    
    public String toString() {
        return "("+a.toString()+", "+b.toString()+")";
    }
}
