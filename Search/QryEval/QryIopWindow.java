import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.*;


public class QryIopWindow extends QryIop {

    protected int operatorDistance;

    public QryIopWindow(int operatorDistance) {
        //System.out.println("\n The distance is "+operatorDistance);
        this.operatorDistance = operatorDistance;
    }

    public int findMinimumDocID() {
        int current, minDocID;
        minDocID = Integer.MAX_VALUE;
        for (Qry q_i : this.args) {
            if (q_i.docIteratorHasMatch(null)) {
                current = q_i.docIteratorGetMatch();
                if (current < minDocID ) {
                    minDocID = current;
                }
            }
        }
        return minDocID;
    }

    public boolean areDocIteratorsinSync(int minDocID) {
        int current ;
        for (Qry q_i : this.args) {
            if (q_i.docIteratorHasMatch(null)) {
                current = q_i.docIteratorGetMatch();
                if (current != minDocID) {
                    return false;
                }
            }

        }
        return true;
    }


    public boolean areAllLocIteratorsvalid() {
        for (int index = 0; index < args.size(); index++) {
            QryIop q_i = (QryIop) this.args.get(index);
            if (q_i.locIteratorHasMatch())
                continue;
            else
                return false;
        }
        return true;
    }

    public boolean areAllDocIteratorsvalid() {
        for (Qry q_i : this.args) {
            if (q_i.docIteratorHasMatch(null))
                continue;
            else
                return false;

        }
        return true;
    }


    @Override
    protected void evaluate() throws IOException {
        this.invertedList = new InvList(this.getField());
        int  minDocID;
        boolean valid = true;

        while (areAllDocIteratorsvalid()) {

            minDocID = findMinimumDocID();
            List<Integer> positions = new ArrayList<Integer>();
            boolean insync = areDocIteratorsinSync(minDocID);

            if (insync) {

                while (areAllLocIteratorsvalid()) {

                    QryIop leftmost = (QryIop) args.get(0);
                    int prev_loc, current_loc, distance;
                    int min=Integer.MAX_VALUE ,max=Integer.MIN_VALUE;
                    prev_loc = leftmost.locIteratorGetMatch();
                    valid = true;
                    for (int i = 1; i < args.size(); i++) {
                        QryIop q = (QryIop) args.get(i);

                        q.locIteratorAdvancePast(prev_loc);

                        if (q.locIteratorHasMatch()) {
                            current_loc = q.locIteratorGetMatch();
                            if (current_loc < min) {
                                min = current_loc;
                            }
                            if (current_loc > max) {
                                max = current_loc;
                            }
                        } else {
                            valid = false;
                            break;
                        }
                    }


                        distance = max - min +1;
                        if(!valid)
                            break;

                        if (distance <= operatorDistance)
                            prev_loc = min;

                        else {
                            for (Qry q_i : this.args) {
                                if (q_i.docIteratorHasMatch(null)) {
                                    int DocId = q_i.docIteratorGetMatch();
                                    if (minDocID == DocId) {
                                        q_i.docIteratorAdvancePast(minDocID);
                                    }
                                }
                            }
                            break;
                        }

                        positions.add(prev_loc);
                        for (Qry q_i : this.args) {
                            ((QryIop) q_i).locIteratorAdvance();
                        }

                }
            }

            if (positions.size() > 0) {
                Collections.sort(positions);
                this.invertedList.appendPosting(minDocID, positions);
            }


            for (Qry q_i : this.args) {
                if (q_i.docIteratorHasMatch(null)) {
                    int DocId = q_i.docIteratorGetMatch();
                    if (minDocID == DocId) {
                        q_i.docIteratorAdvancePast(minDocID);
                    }
                }
            }
        }


    }


}




