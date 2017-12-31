/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the ranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {


    public RetrievalModelIndri(double lambda, double mu) {
        this.lambda = lambda;
        this.mu = mu;
    }

    public RetrievalModelIndri(double lambda, double mu,double fbMU,double fbWeight,int fbDocs,int fbTerms) {
        this.lambda = lambda;
        this.mu = mu;
        this.fbMU=fbMU;
        this.fbWeight=fbWeight;
        this.fbDocs=fbDocs;
        this.fbTerms=fbTerms;
    }


    public RetrievalModelIndri() {
        this.lambda = 0.0;
        this.mu = 0.0;
    }

    public String defaultQrySopName () {
        return new String ("#and");
    }

    public double getLambda() {
        return lambda;
    }

    public void setLambda(double lambda) {
        this.lambda = lambda;
    }

    public double getMu() {
        return mu;
    }

    public void setMu(double mu) {
        this.mu = mu;
    }

    private double mu, lambda,fbMU,fbWeight;
    private int fbDocs;

    public double getFbMU() {
        return fbMU;
    }

    public void setFbMU(double fbMU) {
        this.fbMU = fbMU;
    }

    public double getFbWeight() {
        return fbWeight;
    }

    public void setFbWeight(double fbWeight) {
        this.fbWeight = fbWeight;
    }

    public int getFbDocs() {
        return fbDocs;
    }

    public void setFbDocs(int fbDocs) {
        this.fbDocs = fbDocs;
    }

    public int getFbTerms() {
        return fbTerms;
    }

    public void setFbTerms(int fbTerms) {
        this.fbTerms = fbTerms;
    }

    private int fbTerms;
}
