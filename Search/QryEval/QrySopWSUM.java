/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The SUM operator for all retrieval models.
 */
public class QrySopWSUM extends QrySop {

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
        double score=0.0,total_weight=0.0,weight=0.0;
        int MinDocID;
        if (r instanceof RetrievalModelIndri) {
            if (! this.docIteratorHasMatchCache()) {
                return 0.0;
            } else {

                MinDocID = this.docIteratorGetMatch();
                int size = this.args.size();
                for(int i=0;i<size;i++){
                    total_weight+=this.weight.get(i);
                }
                for(int i=0;i<size;i++){
                    weight=this.weight.get(i);
                    QrySop q_i = (QrySop) this.args.get(i);
                    if(q_i.docIteratorHasMatch(r)){
                        if(q_i.docIteratorGetMatch()==MinDocID){
                            score +=q_i.getScore(r)*(weight/total_weight);
                        }
                    }
                    else{
                        score +=q_i.getScoreDefault(r,MinDocID)*(weight/total_weight);
                    }
                }
            }
        }
        else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the SUM operator.");
        }
        return score;
    }

    public double getScoreDefault(RetrievalModel r,int DocID)throws IOException {
        double score = 0.0, total_weight = 0.0, weight = 0.0;
        int MinDocID;
        int size = this.args.size();
        for (int i = 0; i < size; i++) {
            total_weight += this.weight.get(i);
        }
        for (int i = 0; i < size; i++) {
            weight = this.weight.get(i);
            QrySop q_i = (QrySop) this.args.get(i);

                score += q_i.getScoreDefault(r, DocID) * (weight / total_weight);

        }
        return score;
        }



}
