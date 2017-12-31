/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchMin (r);
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
    }else if(r instanceof RetrievalModelRankedBoolean) {
      if (!this.docIteratorHasMatchCache()) {
        return 0.0;
      } else {
        int currentID = this.docIteratorGetMatch();
        double score = Double.MIN_VALUE, Current = Double.MIN_VALUE;
        for (Qry q : this.args) {
          QrySop q_i = (QrySop) q;
          if (q_i.docIteratorHasMatchCache() && (q_i.docIteratorGetMatch() == currentID)) {
            Current = q_i.getScore(r);
            if (Current > score)
              score = Current;
          }
        }
        return score;
      }
    }

    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the OR operator.");
    }
  }
  public double getScoreDefault(RetrievalModel r,int DocID)throws IOException{
    return 0;
  }
  
  /**
   *  getScore for the UnrankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double  getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }

}
