import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.*;
import java.util.Collections;
import java.io.*;


public class letor {
    Map<Integer,Integer> enabledFeature;
    double k1,b,k3,mu,lambda;
    int numFeatures=18,featureEnabled=18;
    Map<String,String[]> queries = new LinkedHashMap<String,String[]>();
    ArrayList<Document> documentList = new ArrayList<Document>();
    Map<String,ArrayList<Double>> minValues  = new LinkedHashMap<String,ArrayList<Double>>();
    Map<String,ArrayList<Double>> maxvalues  = new LinkedHashMap<String,ArrayList<Double>>();



    public void Main(Map<String,String> parameters) {
        String disabledFeatures;
        disabledFeatures=parameters.get("letor:featureDisable");
        String[] disFeat=null;
        if(disabledFeatures!=null)
            disFeat = disabledFeatures.split(",");
        enabledFeature = new HashMap<Integer, Integer>();
        for (int i = 1; i <= 18; i++) {
            enabledFeature.put(i, 1);
        }
        if(disabledFeatures!=null) {
            for (int i = 0; i < disFeat.length; i++) {
                enabledFeature.put(Integer.parseInt(disFeat[i]), 0);
            }
            featureEnabled=featureEnabled-disFeat.length;
        }
        mu = Double.parseDouble(parameters.get("Indri:mu"));
        lambda = Double.parseDouble(parameters.get("Indri:lambda"));
        k1 = Double.parseDouble(parameters.get("BM25:k_1"));
        b = Double.parseDouble(parameters.get("BM25:b"));
        k3 = Double.parseDouble(parameters.get("BM25:k_3"));
        readQueries(parameters);
       // System.out.println("\nRead queries");
        processDocuments(parameters);
       // System.out.println("\nProcessed DOcuments");

        NormalizeWeights();
       // System.out.println("\nNormalized");

        WritefeatureVector(parameters);
        //System.out.println("\nwrite feature vector");

        try {
            SVMTrainingModel(parameters);
           // System.out.println("\ngot out");

        }catch(Exception e){
            //System.out.println("\n SVM Crashed with the stacktrace");
            e.printStackTrace();
        }
       // System.out.println("\ngot out123");

        getTestingFeature( parameters);
       // System.out.println("\ngot out1111");

        WriteTestFEatureVector(parameters);
       // System.out.println("\ngot ou3213213");

        try {
            runtest(parameters);
        } catch(Exception e){
           // System.out.println("\n SVM Crashed");
        }
        ReRankDocument(parameters);
        publishResult(parameters);



    }


    public void SVMTrainingModel(Map<String,String> parameters)throws Exception {
        String c = parameters.get("letor:svmRankParamC");
        File svmTrainingInputfile = new File(parameters.get("letor:svmRankLearnPath"));
        String execPath1 = svmTrainingInputfile.getAbsolutePath();
        String qrelsFeatureOutputFile = parameters.get("letor:trainingFeatureVectorsFile");
        String modelOutputFile = parameters.get("letor:svmRankModelFile");
        Process cmdProc = Runtime.getRuntime().exec(
                    new String[] { execPath1, "-c", String.valueOf(c), qrelsFeatureOutputFile,
                            modelOutputFile });
        BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            //System.out.println(line);
            //System.out.println("\nstuck1");

        }
        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            //System.out.println(line);
           // System.out.println("\nstuck2");

        }
        int  retValue = cmdProc.waitFor();
        if (retValue != 0) {
           throw new  Exception("SVM Rank crashed !!!!");
        }
       // System.out.println("\nyahan bhi aa gaya");

    }





    public void NormalizeWeights(){

        int document_size=documentList.size();
        ArrayList<Double> minVal,maxVal;
        double min_feature_val=0.0, max_feature_val=0.0, normalizedValue=0.0;
        String docQryID;
        Document temp_doc;
        try{
            for(int doc_idx=0;doc_idx <document_size;doc_idx++){
                temp_doc = documentList.get(doc_idx);
                docQryID = temp_doc.qid;

                if(temp_doc.internalDocID==Integer.MIN_VALUE){
                    continue;
                }

                ArrayList<Double> normalizedFeatureArray = new ArrayList<Double>();
                minVal = minValues.get(docQryID);
                maxVal = maxvalues.get(docQryID);

                if((minVal==null) || (maxVal==null)){
                    continue;
                }
                for(int feature_idx=0;feature_idx<featureEnabled;feature_idx++){

                    min_feature_val = minVal.get(feature_idx);
                    max_feature_val = maxVal.get(feature_idx);

                    if((temp_doc.featuresValues.get(feature_idx)==(double)Integer.MIN_VALUE)){
                    }

                    if((min_feature_val==max_feature_val) ||
                            (temp_doc.featuresValues.get(feature_idx)==(double)Integer.MIN_VALUE)){
                        normalizedFeatureArray.add((double) 0);
                    }else{
                        normalizedValue = (double)(temp_doc.featuresValues.get(feature_idx)-min_feature_val)/(double)(max_feature_val-min_feature_val);
                        normalizedFeatureArray.add(normalizedValue);
                    }
                }

                temp_doc.featuresValues = normalizedFeatureArray;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }


    private void runtest(Map<String,String> parameters) throws Exception{

        String execPath = new File(parameters.get("letor:svmRankClassifyPath")).getAbsolutePath();
        String qrelsFeatureOutputFile = parameters.get("letor:testingFeatureVectorsFile");
        String svmLearnedModel = parameters.get("letor:svmRankModelFile");
        String modelOutputFile = parameters.get("letor:testingDocumentScores");
        Process cmdProc = Runtime.getRuntime().exec(new String[] { execPath, qrelsFeatureOutputFile, svmLearnedModel, modelOutputFile });

        BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            //System.out.println(line);
        }
        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            //System.out.println(line);
        }

        int retValue = cmdProc.waitFor();
        if (retValue != 0) {
            throw new Exception("SVM classify crashed.");
        }
    }

    public double VSM(String[] words,int docId, String field){

        double num=0.0,dln=0.0,qln=0.0,n=0.0;
        int tf=0, df=0;
        TermVector tv;
        try{

            tv = new TermVector(docId , field);
            if(tv.stemsLength()==0){
                return 0.0;
            }

            for(int i=0;i<words.length;i++){

                if(words[i]==null){
                    return 0.0;
                }
                if(tv.indexOfStem(words[i])==-1){
                    continue;
                }
                tf = tv.stemFreq(tv.indexOfStem(words[i]));
                df = tv.stemDf(tv.indexOfStem(words[i]));
                n = (double)Idx.getSumOfFieldLengths(field);
                dln += Math.log((double)(tf+1))*Math.log((double)(tf+1));
                qln += Math.log((double)(n+1)/df)*Math.log((double)(n+1)/df);;
                num += Math.log((double)(tf+1))*Math.log((double)(n+1)/df);
            }

        }catch(Exception e){
            e.printStackTrace();
        }

        if((Math.sqrt(dln)==0) || (Math.sqrt(qln)==0)){
            return 0.0;
        }
        return (double)num/(Math.sqrt(dln)*Math.sqrt(qln));

    }


    public double getFreqRatio(int docId, String field){

        try {
            TermVector tv = new TermVector(docId , field);
            if(tv.stemsLength()==0){
                return 0.0;
            }
            return (double)tv.stemsLength()/(double)tv.positionsLength();

        } catch (IOException e) {
        }

        return 0.0;
    }


    public void getTestingFeature(Map<String,String> parameters){

        String qLine,qid,query;
        String[] qryTerms;
        Map<String,ArrayList<ArrayList<Double>>> featuresetPerQuery=new HashMap<String,ArrayList<ArrayList<Double>>>();
        queries.clear();
        documentList.clear();
        minValues.clear();
        maxvalues.clear();

        try{
            String testQueryFilePath = parameters.get("queryFilePath");
            BufferedReader testInput = new BufferedReader(new FileReader(testQueryFilePath));
            RetrievalModel model =new RetrievalModelBM25(k1,k3,b);

            while ((qLine = testInput.readLine()) != null) {
                int d = qLine.indexOf(':');
                qid = qLine.substring(0, d);
                query = qLine.substring(d + 1);
                qryTerms= QryEval.tokenizeQuery(query);
                queries.put(qid, qryTerms);
            }

            testInput.close();

            BufferedReader testInput2 = new BufferedReader(new FileReader(testQueryFilePath));

            while ((qLine = testInput2.readLine()) != null) {

                int d = qLine.indexOf(':');
                qid = qLine.substring(0, d);
                query = qLine.substring(d + 1);

                ArrayList<Double> Feature = new ArrayList<Double>();
                ArrayList<ArrayList<Double>> featureSet = new ArrayList<ArrayList<Double>>();

                ScoreList r = null;
                r = QryEval.processQuery(query,model);
                r.sort();
                r.truncate(Integer.parseInt(parameters.get("trecEvalOutputLength")));

                for(int doc_idx=0;doc_idx< r.size();doc_idx++){

                    Document new_doc = new Document();
                    new_doc.initializeFeature();
                    new_doc.externalDocID = Idx.getExternalDocid(r.getDocid(doc_idx));
                    new_doc.judgment = Integer.toString(0);
                    new_doc.qid = qid;

                    try{
                        new_doc.internalDocID = Idx.getInternalDocid(new_doc.externalDocID);
                    }catch(Exception e){
                        new_doc.internalDocID = Integer.MIN_VALUE;
                        continue;
                    }
                    new_doc.featuresValues =  extractFeatures(new_doc);
                    featureSet.add(new_doc.featuresValues);
                    documentList.add(new_doc);
                }
                featuresetPerQuery.put(qid,featureSet);
            }
            findMinMax(featuresetPerQuery);
            testInput2.close();
        }catch(Exception e){
            e.printStackTrace();
        }
        NormalizeWeights();
    }


    public void ReRankDocument(Map<String,String> parameters){
        try{
            BufferedReader inputstream = new BufferedReader(new FileReader(parameters.get("letor:testingDocumentScores")));
            String Line=null,score=null;
            int i=0;
            while((Line = inputstream.readLine()) != null){
                score = Line.substring(0, Line.length());
                documentList.get(i).score_str = score;
                documentList.get(i).score = Double.parseDouble(score);
                i++;
            }
            inputstream.close();
        }catch(Exception e){
            e.printStackTrace();
        }

    }





    // Write the results to the file for SVM Training
    public void WritefeatureVector(Map<String,String> parameters){
        String outputString = "";
        Document tempDoc;
        File svmTrainingInputfile = new File(parameters.get("letor:trainingFeatureVectorsFile"));
        try{
            Collections.sort(documentList,new Comparator<Document>(){
                public int compare(Document d1,Document d2){
                    int qid1 = Integer.parseInt(d1.qid);
                    int qid2 = Integer.parseInt(d2.qid);

                    if(qid1 > qid2){
                        return 1;
                    }
                    else if(qid1 < qid2){
                        return -1;
                    }

                    if(d1.score>d2.score){
                            return -1;
                        }
                    else{
                            return 1;
                        }
                }
            });
            if (!svmTrainingInputfile.exists()) {
                svmTrainingInputfile.createNewFile();
            }

            FileWriter fw = new FileWriter(svmTrainingInputfile.getAbsoluteFile(),true);

            BufferedWriter stream = new BufferedWriter(fw);
            for(int doc_idx=0;doc_idx< documentList.size() ;doc_idx++){

                tempDoc = documentList.get(doc_idx);
                if(tempDoc.internalDocID==Integer.MIN_VALUE){
                    continue;
                }

                outputString = "";
                outputString = outputString + tempDoc.judgment + " qid:" + tempDoc.qid + " " ;

                int enable_Feature_index=-1;
                for(int f_idx=1;f_idx<=numFeatures;f_idx++){

                    if(!isFeatureEnabled(f_idx)){
                        continue;
                    }
                    enable_Feature_index++;
                    //System.out.println("\n feature :"+enable_Feature_index+" f_idx"+ f_idx);

                    outputString  =  outputString + Integer.toString(f_idx) + ":";

                    if(tempDoc.featuresValues.get(enable_Feature_index)==(double)Integer.MIN_VALUE){
                        outputString =  outputString + "0.0";
                    }else{
                        outputString = outputString + Double.toString(tempDoc.featuresValues.get(enable_Feature_index)) + " ";
                    }

                }

                outputString = outputString + " # " + tempDoc.externalDocID;
                stream.write(outputString);
                stream.newLine();
            }

            stream.close();
        }catch(Exception e){
            e.printStackTrace();
        }

    }

    public void WriteTestFEatureVector(Map<String,String> parameters){
        String outputString = "";
        Document tempDoc;
        File svmTrainingInputfile = new File(parameters.get("letor:testingFeatureVectorsFile"));
        try{
            Collections.sort(documentList,new Comparator<Document>(){
                public int compare(Document d1,Document d2){
                    int qid1 = Integer.parseInt(d1.qid);
                    int qid2 = Integer.parseInt(d2.qid);

                    if(qid1 > qid2){
                        return 1;
                    }
                    else if(qid1 < qid2){
                        return -1;
                    }

                    if(d1.score>d2.score){
                        return -1;
                    }
                    else{
                        return 1;
                    }
                }
            });
            if (!svmTrainingInputfile.exists()) {
                svmTrainingInputfile.createNewFile();
            }

            FileWriter fw = new FileWriter(svmTrainingInputfile.getAbsoluteFile(),true);

            BufferedWriter stream = new BufferedWriter(fw);
            for(int doc_idx=0;doc_idx< documentList.size() ;doc_idx++){

                tempDoc = documentList.get(doc_idx);
                if(tempDoc.internalDocID==Integer.MIN_VALUE){
                    System.out.println("Skipping doc as external doc id not found");
                    continue;
                }

                outputString = "";
                outputString = outputString + tempDoc.judgment + " qid:" + tempDoc.qid + " " ;

                int enable_Feature_index=-1;
                for(int f_idx=1;f_idx<=numFeatures;f_idx++){

                    if(!isFeatureEnabled(f_idx)){
                        continue;
                    }
                    enable_Feature_index++;

                    outputString  =  outputString + Integer.toString(f_idx) + ":";

                    if(tempDoc.featuresValues.get(enable_Feature_index)==Integer.MIN_VALUE){
                        outputString =  outputString + "0.0";
                    }else{
                        outputString = outputString + Double.toString(tempDoc.featuresValues.get(enable_Feature_index)) + " ";
                    }

                }

                outputString = outputString + " # " + tempDoc.externalDocID;
                stream.write(outputString);
                stream.newLine();
            }

            stream.close();
        }catch(Exception e){
            e.printStackTrace();
        }

    }




    public void processDocuments(Map<String,String> parameters) {
        Map<String,ArrayList<ArrayList<Double>>> featuresetPerQuery=new HashMap<String,ArrayList<ArrayList<Double>>>();
        ArrayList<ArrayList<Double>> featureSet=new ArrayList<ArrayList<Double>>();
        String line,prevQid=null;
        try {
            BufferedReader inputStream = new BufferedReader(new FileReader(parameters.get("letor:trainingQrelsFile")));
            line=inputStream.readLine();
            while(line!=null){
                String[] tokens = line.split("\\s+");
                Document doc=new Document();
                doc.initializeFeature();
                doc.qid=tokens[0];
                doc.externalDocID=tokens[2].trim();
                doc.judgment=tokens[3].trim();
                try {
                    doc.internalDocID = Idx.getInternalDocid(doc.externalDocID);
                }catch(Exception e){
                    doc.internalDocID=Integer.MIN_VALUE;
                }
                doc.featuresValues=extractFeatures(doc);
                documentList.add(doc);
                if((prevQid!=null) && (!prevQid.equals(doc.qid))){
                    featuresetPerQuery.put(prevQid,featureSet);
                    featureSet.clear();
                }
                prevQid = doc.qid;
                featureSet.add(doc.featuresValues);
                //System.out.println("\nADDING"+ doc.qid);
                line=inputStream.readLine();
            }
            featuresetPerQuery.put(prevQid,featureSet);
            findMinMax(featuresetPerQuery);
        }
        catch(Exception e){

        }
    }

    public void findMinMax(Map<String,ArrayList<ArrayList<Double>>> featureSetPerQuery){
        ArrayList<Double> temp_array ;
        ArrayList<Double> minFeatureValue ,maxFeatureValue;
        for(String qid : featureSetPerQuery.keySet()){

            ArrayList<ArrayList<Double>> tempFeatureValue = featureSetPerQuery.get(qid);
            minFeatureValue = new ArrayList<Double>();
            maxFeatureValue = new ArrayList<Double>();
            double min_val,max_val;
            double feature_val=0.0;
            for(int feature_idx=0;feature_idx<featureEnabled;feature_idx++){
                 min_val = Integer.MAX_VALUE;
                 max_val = Integer.MIN_VALUE;
                for(int doc_idx=0;doc_idx<tempFeatureValue.size();doc_idx++){
                    temp_array = tempFeatureValue.get(doc_idx);
                    if(temp_array.get(feature_idx)==Integer.MIN_VALUE){
                        continue;
                    }
                    if(feature_idx==6)
                    feature_val=temp_array.get(feature_idx);

                    if(temp_array.get(feature_idx) > max_val){
                        max_val = temp_array.get(feature_idx);
                    }else if(temp_array.get(feature_idx) < min_val){
                        min_val = temp_array.get(feature_idx);
                    }
                }
                if(feature_idx==6) {

                }minFeatureValue.add(min_val);
                maxFeatureValue.add(max_val);
            }
            minValues.put(qid, minFeatureValue);
            maxvalues.put(qid, maxFeatureValue);
        }
    }

    public ArrayList<Double> extractFeatures(Document doc) {
            ArrayList<Double> result= new ArrayList<Double>();
        if(doc.internalDocID==Integer.MIN_VALUE){

            for(int f_idx=0;f_idx<=numFeatures;f_idx++){
                result.add((double) Integer.MIN_VALUE);
            }
            return result;
        }

        if(isFeatureEnabled(1)){
                try {
                    result.add((double) (Integer.parseInt(Idx.getAttribute("spamScore", doc.internalDocID))));
                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 1 with exception " + e);
                }
            }
            if(isFeatureEnabled(2)){
                try {
                    String rawUrl = Idx.getAttribute("rawUrl", doc.internalDocID);
                    int depth=0;
                    for(int idx=0;idx<rawUrl.length();idx++){
                        if(rawUrl.charAt(idx)=='/') {
                            depth++;
                        }
                    }

                    result.add((double) depth);

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 2 with exception " + e);

                }
            }
            if(isFeatureEnabled(3)){
                try {
                    String rawUrl = Idx.getAttribute("rawUrl", doc.internalDocID);
                    if (rawUrl.indexOf("wikipedia.org") != -1) {
                        result.add((double)1);

                    }
                    else
                        result.add((double)0);

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 3 with exception " + e);
                }
            }
            if(isFeatureEnabled(4)){
                try {
                    result.add((double) (Float.parseFloat(Idx.getAttribute("PageRank", doc.internalDocID))));

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
            if(isFeatureEnabled(5)){
                try {
                    String[] stems=queries.get(doc.qid);
                result.add(get_BM25_feature_for_field(stems,doc.internalDocID,"body"));

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
            if(isFeatureEnabled(6)){
                try {
                    String[] stems=queries.get(doc.qid);
                    //System.out.println("the value of feature 6 is "+featureIndri(stems,doc.internalDocID,"body"));
                    result.add(featureIndri(stems,doc.internalDocID,"body"));

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
            if(isFeatureEnabled(7)){
                try {
                    String[] stems=queries.get(doc.qid);
                    result.add(featureTermOverlap(stems,doc.internalDocID,"body"));

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
            if(isFeatureEnabled(8)){
                try {
                    String[] stems=queries.get(doc.qid);

                    result.add(get_BM25_feature_for_field(stems,doc.internalDocID,"title"));

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
            if(isFeatureEnabled(9)){
                try {
                    String[] stems=queries.get(doc.qid);
                    result.add(featureIndri(stems,doc.internalDocID,"title"));

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
            if(isFeatureEnabled(10)){
                try {
                    String[] stems=queries.get(doc.qid);
                    result.add(featureTermOverlap(stems,doc.internalDocID,"title"));

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
            if(isFeatureEnabled(11)){
                try {
                    String[] stems=queries.get(doc.qid);
                    result.add(get_BM25_feature_for_field(stems,doc.internalDocID,"url"));

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
            if(isFeatureEnabled(12)){
                try {
                    String[] stems=queries.get(doc.qid);
                    result.add(featureIndri(stems,doc.internalDocID,"url"));

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
            if(isFeatureEnabled(13)){
                try {
                    String[] stems=queries.get(doc.qid);
                    result.add(featureTermOverlap(stems,doc.internalDocID,"url"));

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
            if(isFeatureEnabled(14)){
                try {
                    String[] stems=queries.get(doc.qid);
                    result.add(get_BM25_feature_for_field(stems,doc.internalDocID,"inlink"));


                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
            if(isFeatureEnabled(15)){
                try {
                    String[] stems=queries.get(doc.qid);
                    result.add(featureIndri(stems,doc.internalDocID,"inlink"));

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
            if(isFeatureEnabled(16)){
                try {
                    String[] stems=queries.get(doc.qid);
                    result.add(featureTermOverlap(stems,doc.internalDocID,"inlink"));

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
            if(isFeatureEnabled(17)){
                try {
                    result.add(getFreqRatio(doc.internalDocID,"body"));

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
            if(isFeatureEnabled(18)){
                try {
                    String[] stems=queries.get(doc.qid);
                    result.add(VSM(stems,doc.internalDocID,"body"));

                }
                catch(Exception e){
                    System.out.println("\nError extracting feature 4 with exception " + e);
                }
            }
        return result;
    }


    public double get_BM25_feature_for_field(String[] words,int docId, String field){

        double score = 0,tf,num_docs,tot_len,doc_length,average_len,idf,tf_weight,df;
        int stemIdx;
        TermVector tv;


        try{
            tv = new TermVector(docId , field);
            int stem_length = tv.stemsLength();
            if(stem_length==0){
                return (double)Integer.MIN_VALUE;
            }
            for(int idx=0;idx<words.length;idx++){
                String currentWord=words[idx];
                if(!doesWordExist(tv,currentWord)){
                    continue;
                }
                else {
                    stemIdx = tv.indexOfStem(words[idx]);
                    num_docs = (double) Idx.getDocCount(field);
                    tot_len = (double) Idx.getSumOfFieldLengths(field);
                    doc_length = (double) Idx.getFieldLength(field, docId);
                    tf = (double) tv.stemFreq(stemIdx);
                    df = (double) tv.stemDf(stemIdx);
                    average_len = tot_len / num_docs;
                    idf = Math.max(Math.log(Idx.getNumDocs() - df + 0.50) - Math.log(df + 0.50), 0.00);
                    tf_weight = tf / (tf + k1 * ((1 - b) + (b * doc_length / average_len)));
                    score = score + idf * tf_weight;
                }
            }

        }catch(Exception e){
            System.out.println("\n Error getting the BM25 score:"+ e);
        }
        return score;
    }



    public double featureIndri(String[] words,int docId, String field){
        double score = 1.0,tot_len,doc_length,tf,ctf,prior;
        TermVector tv;
        int count=0,stem_length;
        try{
            tv = new TermVector(docId , field);
            tot_len = (double)Idx.getSumOfFieldLengths(field);
            doc_length = (double)Idx.getFieldLength(field,docId);
            if((tv.positionsLength()==0)){
                return (double)Integer.MIN_VALUE;
            }

            for(int i =0;i<words.length;i++){
                if(!doesWordExist(tv,words[i])){
                    count++;
                }
            }

            if(count==words.length){
                return 0.0;
            }

            for(int i=0;i<words.length;i++){

                if(doesWordExist(tv,words[i])){
                     tf = (double)tv.stemFreq(tv.indexOfStem(words[i]));
                     ctf = (double)tv.totalStemFreq(tv.indexOfStem(words[i]));
                     prior = ctf/tot_len;
                     score *= ((1-lambda)*((double)tf+(mu*prior))/(doc_length + mu)) + (lambda)*prior;

                }
                else{
                     ctf = (double)Idx.getTotalTermFreq(field,words[i]);
                     prior = ctf/tot_len;
                     score *= ((1-lambda)*(mu*prior)/(doc_length + mu)) + (lambda)*prior;
                }

            }

        }catch(Exception e){
            System.out.println("\n Error getting the indri score");
        }
        return (Math.pow(score,1/(double)words.length));
    }

    public double featureTermOverlap(String[] words,int docId, String field){

        TermVector tv;
        int num_words_match=0;

       /* if(words.length==0){
            return 0.00;
        }*/


        try{
            tv = new TermVector(docId , field);
            if(tv.positionsLength()==0)
                return Integer.MIN_VALUE;
            for(int idx=0;idx<words.length;idx++){
                if(tv.indexOfStem(words[idx])!=-1){
                    num_words_match++;
                }
            }

        }catch(Exception e){
            e.printStackTrace();
        }

        return (num_words_match)/(double)(words.length);
    }


    public boolean doesWordExist(TermVector tv,String word){
        if(tv.indexOfStem(word)!=-1){
            return true;
        }
        else
         return false;
    }



    public boolean isFeatureEnabled(int featureNumber){
            if(enabledFeature.get(featureNumber)==1)
                return true;
            else
                return false;
        }


        public void readQueries(Map<String,String > parameters){

        String[] terms;
        String line ,qid,query;
        int index;
        try {
            String trainingQueryFilePath = parameters.get("letor:trainingQueryFile");
            BufferedReader inputStream = new BufferedReader(new FileReader(trainingQueryFilePath));
            while ((line = inputStream.readLine()) != null) {
                index = line.indexOf(':');
                qid = line.substring(0, index);
                query = line.substring(index + 1);
                terms= QryEval.tokenizeQuery(query);
                queries.put(qid, terms);
            }
            inputStream.close();
        }catch (IOException e) {
        }
    }

    public void publishResult(Map<String,String > parameters){
        int i=0;
        try{
            Collections.sort(documentList,new Comparator<Document>(){
                public int compare(Document d1,Document d2){
                    int qid1 = Integer.parseInt(d1.qid);
                    int qid2 = Integer.parseInt(d2.qid);

                    if(qid1 > qid2){
                        return 1;
                    }
                    else if(qid1 < qid2){
                        return -1;
                    }

                    if(d1.score>d2.score){
                        return -1;
                    }
                    else{
                        return 1;
                    }
                }
            });
            File file = new File(parameters.get("trecEvalOutputPath"));
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fw = new FileWriter(file.getAbsoluteFile(),true);
            BufferedWriter output = new BufferedWriter(fw);
            Document doc = null;
            String QryResult = null;

            for(i=0;i<documentList.size();i++){
                doc = documentList.get(i);
                QryResult = doc.qid + " Q0 " +  doc.externalDocID + " " + (new Integer(i+1)).toString() + " " + doc.score_str + " run-1 ";
                output.write(QryResult);
                output.newLine();
            }
            output.close();
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
}
