/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopAND extends QrySop {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
        if(r instanceof RetrievalModelIndri)
            return this.docIteratorHasMatchMin(r);
        else
            return this.docIteratorHasMatchAll (r);
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
            if (! this.docIteratorHasMatchCache()) {
                return 0.0;
            } else {
                double score=Double.MAX_VALUE,Current=Double.MAX_VALUE;
                for(Qry q : this.args) {
                    Current=((QrySop) q).getScore(r);
                    if(Current<score)
                        score=Current;
                }
                return score;
            }
        }
        else if (r instanceof RetrievalModelIndri) {
            int MinDocID = this.docIteratorGetMatch();
            double score = 1.0;
            int size = this.args.size();
            for(int i=0;i<size;i++){
                QrySop q_i = (QrySop) this.args.get(i);
                if(q_i.docIteratorHasMatch(r) && (q_i.docIteratorGetMatch()==MinDocID)){
                    score *= q_i.getScore(r);
                }else{
                    score *= q_i.getScoreDefault(r,MinDocID);
                }
            }
            score=Math.pow(score,1/(double)size);
            return score;
        }
        else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the OR operator.");
        }
    }

    public double getScoreDefault(RetrievalModel r,int DocID)throws IOException{
        double score = 1.0;
        int size = this.args.size();
        for(int i=0;i<size;i++){
            QrySop q_i = (QrySop) this.args.get(i);

                score *= q_i.getScoreDefault(r,DocID);

        }
        score=Math.pow(score,1/(double)size);
        return score;
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
