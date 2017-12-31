/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the ranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {

    public RetrievalModelBM25(double k1, double k2, double b) {
        K1 = k1;
        K2 = k2;
        this.b = b;
    }

    public RetrievalModelBM25() {
        K1 = 0.0;
        K2 = 0.0;
        this.b = 0.0;
    }

    public String defaultQrySopName () {
        return new String ("#sum");
    }

    private double K1,K2,b;

    public double getK1() {
        return K1;
    }

    public void setK1(double k1) {
        K1 = k1;
    }

    public double getK2() {
        return K2;
    }

    public void setK2(double k2) {
        K2 = k2;
    }

    public double getB() {
        return b;
    }

    public void setB(double b) {
        this.b = b;
    }


}
