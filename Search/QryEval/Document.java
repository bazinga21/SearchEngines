import java.util.*;

public class Document {


    public String qid;

    public String externalDocID;
    public int internalDocID;
    public String judgment;
    public double score;
    public String score_str;
    ArrayList<Double> featuresValues;

    public void initializeFeature(){
        featuresValues = new ArrayList<Double>();
        score =0.0;

    }


}
