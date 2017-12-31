

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.io.*;


public class Diversity {
    LinkedHashMap<String,Integer> numberOfIntents;
    Map<String,String> parameters;
    LinkedHashMap<String,String> queries;
    LinkedHashMap<String,ScoreList> query2score;
    String diversityAlgo;
    LinkedHashMap<String,Double> results;
    LinkedHashMap<String,Double> scalingFactor;
    BufferedWriter output;
    Boolean scaling_needed;
    RetrievalModel model;
    Map<Integer,Double> slots;
    Map<Integer,Double> priority;


    public void Main() throws Exception {

    parseQueries();

    if(parameters.containsKey("diversity:initialRankingFile")){
        String qLine,q_id,doc_id;
        Double score;
        ScoreList temp;
        BufferedReader inputstream = new BufferedReader(new FileReader(parameters.get("diversity:initialRankingFile")));
        while ((qLine = inputstream.readLine()) != null) {
            String[] tokens = qLine.split("\\s");
            q_id=tokens[0];
            doc_id=tokens[2];
            score=Double.parseDouble(tokens[4]);
            if(score>1.0)
                scaling_needed=true;
            if(!query2score.containsKey(q_id)){
                ScoreList scoreList=new ScoreList();
                scoreList.add(Idx.getInternalDocid(doc_id),score);
                query2score.put(q_id,scoreList);
            }
            else {
                temp = query2score.get(q_id);
                temp.add(Idx.getInternalDocid(doc_id), score);
                temp.sort();
                query2score.put(q_id, temp);
            }
        }
        inputstream.close();
        initialize_scaling_factor();
        truncateLists();

        if(scaling_needed)
            find_scaling_factor();
        if(this.diversityAlgo.equals("xQuAD")){
            xQuadScore();
        }
        else if(this.diversityAlgo.equals("PM2"))
            PM2Score();

    }
    else{
        fetchDocuments();

        if(parameters.get("retrievalAlgorithm").equals("Indri"))
            scaling_needed=false;
        else
            scaling_needed=true;
        initialize_scaling_factor();
        truncateLists();
        if(scaling_needed)
            find_scaling_factor();
        if(this.diversityAlgo.equals("xQuAD")){
            xQuadScore();
        }
        else if(this.diversityAlgo.equals("PM2"))
            PM2Score();

    }

    }



    void fetchDocuments() throws Exception{

    for(Map.Entry<String,String> iterator:queries.entrySet()){
        String q_id=iterator.getKey();
        String query=iterator.getValue();
        ScoreList scores;
        scores=QryEval.processQuery(query,model);
        scores.sort();
        query2score.put(q_id,scores);
    }

    }


    void truncateLists(){
        for(Map.Entry<String,Integer> entry: numberOfIntents.entrySet()){
         ScoreList list= query2score.get(entry.getKey());
         int cutOff=0;
         list.truncate(Integer.parseInt(parameters.get("diversity:maxInputRankingsLength")));
         query2score.put(entry.getKey(),list);
         cutOff=list.size();
         for(int i=1;i<=entry.getValue();i++){
             String id=entry.getKey()+'.'+i;
             ScoreList scores= query2score.get(id);
             scores.truncate(cutOff);
             query2score.put(id,scores);
         }
        }
    }






    void initialize_scaling_factor(){
        for(Map.Entry<String,Integer> entry: numberOfIntents.entrySet()){
            scalingFactor.put(entry.getKey(),1.0);
        }

    }


    void find_scaling_factor() throws Exception{
        for(Map.Entry<String,Integer> entry: numberOfIntents.entrySet()){
            String q_id;
            Double scaling_factor=Double.MIN_VALUE;
            ScoreList current_entry,main_list=null;
            for(int i=0;i<=entry.getValue();i++){
                if(i==0)
                    q_id=entry.getKey();
                else
                    q_id=entry.getKey()+'.'+i;

                current_entry=query2score.get(q_id);
                if(i==0) {
                    main_list=current_entry;
                }

                Double temp=0.0;
                for(int j=0;j<current_entry.size();j++){
                    if(main_list.containsDocument(Idx.getExternalDocid(current_entry.getDocid(j)))) {
                        temp += current_entry.getDocidScore(j);
                        //System.out.println("\n Document ID :"+ Idx.getExternalDocid(current_entry.getDocid(j))+" intent: "+q_id+" score: "+ current_entry.getDocidScore(j));
                    }
                    else {
                        //System.out.println("\n MISMATCH Document Id : "+Idx.getExternalDocid(current_entry.getDocid(j))+" present in intent: "+ q_id+" but not in main list");
                    }

                }
                //System.out.println("\n The scaling factor is "+temp+" intent "+ q_id);
                if(temp>scaling_factor)
                    scaling_factor=temp;
            }
            scalingFactor.put(entry.getKey(),scaling_factor);
        }
    }


    void xQuadScore() throws Exception{
        int maxInputLength,maxResultLength,numberOfIntent;
        String qid;
        ScoreList initialRanking,diversifiedRanking;
        maxInputLength=Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));
        maxResultLength=Integer.parseInt(parameters.get("diversity:maxResultRankingLength"));
        Double lambda= Double.parseDouble(parameters.get("diversity:lambda"));
        ScoreList result;
        Double scale=1.0;

        for(Map.Entry<String,Integer> entry : numberOfIntents.entrySet()){
            qid=entry.getKey();//q
            scale=scalingFactor.get(qid);
            numberOfIntent=entry.getValue();//number of intent
            initialRanking=query2score.get(qid);//initial ranking
            //String currMaxDoc=null;
           // Double currMaxScore=0.0;
            Double initialScore=0.0;
            Double intentProbability;
            result=new ScoreList();

            //diversifiedRanking= new LinkedHashMap<String,Double>();
            while(result.size()<maxResultLength){
                //System.out.println("\nStuck here: "+ qid+" "+ result.size());

                String currMaxDoc=null;
                Double currMaxScore=Double.MIN_VALUE;
                  for(int k=0;k<initialRanking.size();k++){
                      String currID=Idx.getExternalDocid(initialRanking.getDocid(k));
                      if(result.containsDocument(currID)){
                          System.out.println("\nThis should not have happened");
                          continue;
                      }
                      Double currscore=0.0,diversityScore=0.0;
                      initialScore=initialRanking.get_score_for_document(currID)/scale;//P(d/q)
                      currscore=(1-lambda)*initialScore;//(1-lambda)*P(d/q)
                      intentProbability=1.0/numberOfIntent;//P(qi/q)
                      for(int i=1;i<=numberOfIntent;i++){
                          Double currDiversityscore=0.0;
                          String currentIntentID=qid+'.'+i;//q.i
                          Double docIntentScore=(query2score.get(currentIntentID).get_score_for_document(currID)); //P(d/qi)
                          if(docIntentScore==null)
                              docIntentScore=0.0;
                          else
                              docIntentScore=docIntentScore/scale;
                          //System.out.println("\nThe doc intent Score is "+docIntentScore);
                          Double productFactor=1.0;
                          for(int l=0;l<result.size();l++){
                              Double coverage=(query2score.get(currentIntentID).get_score_for_document(Idx.getExternalDocid(result.getDocid(l))));
                              if (coverage==null)
                                  coverage=0.0;
                              else
                                  coverage=coverage/scale;
                              productFactor*=(1-coverage);//(1-P(dj/qi))
                          }
                          currDiversityscore=intentProbability*docIntentScore*productFactor;
                          diversityScore+=currDiversityscore;
                      }
                      currscore+=lambda*diversityScore;
                      if(currscore>currMaxScore) {
                          currMaxScore = currscore;
                          currMaxDoc=currID;

                      }

                  }
                 // diversifiedRanking.put(currMaxDoc,currMaxScore);
                 try {
                      initialRanking.remove(currMaxDoc);
                     result.add(Idx.getInternalDocid(currMaxDoc), currMaxScore);
                 }
                 catch(Exception e){

                 }
            }
            result.sort();
            try {
                printResult(result, qid);
            }catch (Exception e){

            }

        }


    }

    void PM2Score() throws Exception{
        int maxInputLength,maxResultLength,numberOfIntent;
        double voting=0.0;
        String qid;
        ScoreList initialRanking,diversifiedRanking;
        maxInputLength=Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));
        maxResultLength=Integer.parseInt(parameters.get("diversity:maxResultRankingLength"));
        Double lambda= Double.parseDouble(parameters.get("diversity:lambda"));
        ScoreList result;
        Double scale=1.0;

        for(Map.Entry<String,Integer> entry : numberOfIntents.entrySet()){
            qid=entry.getKey();//q
            scale=scalingFactor.get(qid);
            numberOfIntent=entry.getValue();//number of intent
            voting=maxResultLength/numberOfIntent;
            initialRanking=query2score.get(qid);//initial ranking
            Double initialScore=0.0;
            Double intentProbability;
            result=new ScoreList();
            slots=new HashMap<Integer,Double>();
            priority=new HashMap<Integer,Double>();
            initializeSlots(slots,numberOfIntent);
            int next_intent;

            while(result.size()<maxResultLength){
                String currMaxDoc=null;
                Double currMaxScore=Double.MIN_VALUE;
                next_intent=initializepriority(numberOfIntent,voting);
                for(int k=0;k<initialRanking.size();k++){
                    String currID=Idx.getExternalDocid(initialRanking.getDocid(k));
                    if(result.containsDocument(currID)){
                        System.out.println("\nThis should not have happened");
                        continue;
                    }
                    Double currscore=0.0,diversityScore=0.0;
                    currscore=lambda*priority.get(next_intent)*(query2score.get(qid+'.'+next_intent).get_score_for_document(currID))/scale; //lambda*qt[i]*p(d/qi)
                    for(int l=1;l<=numberOfIntent;l++){
                        if(l==next_intent)
                            continue;
                        diversityScore+=priority.get(l)*(query2score.get(qid+'.'+l).get_score_for_document(currID))/scale;//qt[i]*p(d/qi) i!=next intent

                    }
                    currscore+=(1-lambda)*diversityScore;

                    if(currscore>currMaxScore) {
                        currMaxScore = currscore;
                        currMaxDoc=currID;

                    }

                }
                try {
                    initialRanking.remove(currMaxDoc);
                    result.add(Idx.getInternalDocid(currMaxDoc), currMaxScore);
                }
                catch(Exception e){
                }
               updateCoverage(numberOfIntent,qid,currMaxDoc,scale);


            }
            result.sort();
            try {
                printResult(result, qid);
            }catch (Exception e){

            }

        }


    }

    void updateCoverage(int numofIntents,String qid,String maxDoc,Double scale){
        Double total_score=0.0;
        for(int i=1;i<=numofIntents;i++){
            total_score+=(query2score.get(qid+'.'+i).get_score_for_document(maxDoc))/scale;

        }
        for(int k=1;k<=numofIntents;k++){
            double update =slots.get(k)+ (((query2score.get(qid+'.'+k).get_score_for_document(maxDoc)))/scale)/total_score;
            slots.put(k,update);
        }

    }




    int initializepriority(int numOdIntent,Double voting){
        double max=Double.MIN_VALUE,priority;
        int maxIndent=0;

        for(int i=1;i<=numOdIntent;i++){
            priority=voting/((2*slots.get(i))+1);
            if(priority>max){
                max=priority;
                maxIndent=i;
            }
            this.priority.put(i,priority);
        }

        return maxIndent;



    }





void initializeSlots(Map<Integer,Double> slots,int numOfIntents){
        for (int i=1;i<=numOfIntents;i++){
            slots.put(i,0.0);

    }
    return;
}





  void printResult(ScoreList result,String qid) throws Exception{

      File file = new File(parameters.get("trecEvalOutputPath"));
      if (!file.exists()) {
          file.createNewFile();
      }
      FileWriter fw = new FileWriter(file.getAbsoluteFile(),true);
      BufferedWriter output = new BufferedWriter(fw);

      try {
        for (int i = 0; i < result.size(); i++) {
            String line=qid + " Q0" + " " + Idx.getExternalDocid(result.getDocid(i)) + " " + (i + 1) + " " + result.getDocidScore(i) + " " + "reference";
           // System.out.println(line);
            output.write(line);
            output.newLine();
        }

        output.close();
    }catch(Exception e){

          System.out.println("\n  "+e);

    }

  }

    void parseQueries() throws  Exception{
        String qLine;
        BufferedReader inputstream = new BufferedReader(new FileReader(parameters.get("queryFilePath")));
        while ((qLine = inputstream.readLine()) != null) {
            int d = qLine.indexOf(':');
            if (d < 0) {
                throw new IllegalArgumentException
                        ("Syntax error:  Missing ':' in query line.");
            }
            String qid = qLine.substring(0, d);
            String query = qLine.substring(d + 1);
            queries.put(qid,query);

            if(!qid.contains(".")){
                if(!numberOfIntents.containsKey(qid))
                    numberOfIntents.put(qid,0);
            }
            System.out.println("Query " + qLine);
            parseIntents();
        }
        inputstream.close();
    }

    void parseIntents() throws Exception{
        String qLine;
        BufferedReader inputstream = new BufferedReader(new FileReader(parameters.get("diversity:intentsFile")));
        while ((qLine = inputstream.readLine()) != null) {
            int d = qLine.indexOf(':');
            if (d < 0) {
                throw new IllegalArgumentException
                        ("Syntax error:  Missing ':' in query line.");
            }
            String qid = qLine.substring(0, d);
            String query = qLine.substring(d + 1);
            queries.put(qid,query);
            int ind = qid.indexOf('.');
            String intentID=qid.substring(ind+1);
            String qID=qid.substring(0,ind);
            if(numberOfIntents.containsKey(qID)){
                int currentIntentID= numberOfIntents.get(qID);
                if(Integer.parseInt(intentID)>currentIntentID)
                    numberOfIntents.put(qID,Integer.parseInt(intentID));
            }
      //      else
      //          System.out.println("\nWhat the heck!!!");
      //      System.out.println("Query  Intent" + qLine);
        }
        inputstream.close();
    }




    public Diversity(Map<String,String> params, RetrievalModel model){
        this.parameters=params;
        numberOfIntents=new LinkedHashMap<String,Integer>();
        queries=new LinkedHashMap<String,String>();
        query2score= new LinkedHashMap<String,ScoreList>();
        results= new LinkedHashMap<String,Double>();
        scalingFactor= new LinkedHashMap<String,Double>();
        this.diversityAlgo=params.get("diversity:algorithm");
        scaling_needed=false;
        this.model=model;
    }




}
