/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {


  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    }
    else if(r instanceof RetrievalModelRankedBoolean){
      return this.getScoreRankedBoolean(r);
    }
    else if(r instanceof RetrievalModelBM25){
      return this.getScoreBM25(r);
    }
    else if(r instanceof RetrievalModelIndri){
      return this.getScoreIndri(r);
    }
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }

  public double getScoreDefault (RetrievalModel r,int docid) throws IOException {

    if(r instanceof RetrievalModelIndri){
      return this.getScoreDefaultIndri(r,docid);
    }
    else {
      throw new IllegalArgumentException
              (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }




  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }

  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      QryIop term = (QryIop)this.args.get(0);
      return term.docIteratorGetMatchPosting().tf;
    }
  }

  public double getScoreBM25 (RetrievalModel r) throws IOException {
    double tf,df,N,idf,tf_weight;
    double score,k1,b,tot_len,doc_avg,doc_len;
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      QryIop q_i = (QryIop) this.args.get(0);
      k1 = ((RetrievalModelBM25) r).getK1();
      b = ((RetrievalModelBM25) r).getB();
      tf =(double) q_i.getTF();
      df = (double)q_i.getDf();
      N = (double) Idx.getNumDocs();
      tot_len =getTotalLength(q_i);
      doc_len = getDocLength(q_i);
      doc_avg = tot_len/Idx.getDocCount(q_i.field);
      tf_weight = tf/(tf + k1*((1-b) + (b*(doc_len/doc_avg))));
      idf = Math.max(Math.log((N - df +0.5)/(df+ 0.5)),0.00);
      score=idf*tf_weight;
      return score;
    }
  }

  public double getScoreIndri (RetrievalModel r) throws IOException {
   // System.out.println("\nGOt in default of Indri");

    double tf,ctf;
    double tot_len,doc_len,mu,lambda,conditional_probability,score=0.0;

      mu = ((RetrievalModelIndri) r).getMu();
      lambda = ((RetrievalModelIndri) r).getLambda();
      QryIop q_i = (QryIop) this.args.get(0);
      tf = (double)q_i.getTF();
      ctf = (double)q_i.getCtf();
      tot_len =getTotalLength(q_i);
      conditional_probability=ctf/tot_len;
      doc_len = getDocLength(q_i);
      score=(((1- lambda)*((tf+(mu*conditional_probability))/(doc_len + mu))) + (lambda)*conditional_probability);
      return score;

  }


  public double getScoreDefaultIndri(RetrievalModel r,int DocID)throws IOException{
    //System.out.println("\nDefault score11");

    double ctf;
    double tot_len,doc_len,mu,lambda,conditional_probability,score=0.0;
    mu = ((RetrievalModelIndri) r).getMu();
    lambda = ((RetrievalModelIndri) r).getLambda();
    QryIop q_i = (QryIop) this.args.get(0);
    ctf = (double)q_i.getCtf();
    tot_len =getTotalLength(q_i);
    conditional_probability=ctf/tot_len;
    doc_len = (double)Idx.getFieldLength(q_i.field,DocID);
    score=(((1- lambda)*(((mu*conditional_probability))/(doc_len + mu))) + (lambda)*conditional_probability);
    return score;

  }



  public double getTotalLength(QryIop q_i)throws IOException{
    return (double)Idx.getSumOfFieldLengths(q_i.field);

  }


  public double getDocLength(QryIop q_i)throws IOException{
    return (double)Idx.getFieldLength(q_i.field,q_i.docIteratorGetMatch());

  }

  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get (0);
    q.initialize (r);
  }

}
