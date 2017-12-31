/*
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.3.1.
 */
import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Analyzer.*;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import java.text.DecimalFormat;

/**
 *  This software illustrates the architecture for the portion of a
 *  search engine that evaluates queries.  It is a guide for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 */
public class QryEval {

  //  --------------- Constants and variables ---------------------

  private static final String USAGE =
    "Usage:  java QryEval paramFile\n\n";

  private static final String[] TEXT_FIELDS =
    { "body", "title", "url", "inlink" };


  private static final EnglishAnalyzerConfigurable ANALYZER =
          new EnglishAnalyzerConfigurable();

  //  --------------- Methods ---------------------------------------

  /**
   *  @param args The only argument is the parameter file name.
   *  @throws Exception Error accessing the Lucene index.
   */
  public static void main(String[] args) throws Exception {

    //  This is a timer that you may find useful.  It is used here to
    //  time how long the entire program takes, but you can move it
    //  around to time specific parts of your code.
    
    Timer timer = new Timer();
    timer.start ();

    //  Check that a parameter file is included, and that the required
    //  parameters are present.  Just store the parameters.  They get
    //  processed later during initialization of different system
    //  components.

    if (args.length < 1) {
      throw new IllegalArgumentException (USAGE);
    }

    Map<String, String> parameters = readParameterFile (args[0]);
    RetrievalModel model=null;
    //  Open the index and initialize the retrieval model.

    Idx.open (parameters.get ("indexPath"));

    //  Perform experiments.

    /*if(parameters.get("retrievalAlgorithm").equals("letor")){
       letor l = new letor();
       l.Main(parameters);

    }
    else*/ if (parameters.containsKey("diversity") && parameters.get("diversity").equals("true")){
      if(parameters.containsKey("retrievalAlgorithm"))
        model = initializeRetrievalModel (parameters);
      Diversity diversity=new Diversity(parameters,model);
      diversity.Main();

    }
    else{
    //  processQueryFile(parameters, model);
    }
    //  Clean up.
    
    timer.stop ();
    System.out.println ("Time:  " + timer);
  }

  /**
   *  Allocate the retrieval model and initialize it using parameters
   *  from the parameter file.
   *  @return The initialized retrieval model
   *  @throws IOException Error accessing the Lucene index.
   */
  private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
    throws IOException {

    RetrievalModel model = null;
    String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

    if (modelString.equals("unrankedboolean")) {
      model = new RetrievalModelUnrankedBoolean();
    }
    else if (modelString.equals("rankedboolean")) {
      model = new RetrievalModelRankedBoolean();
    }
    else if(modelString.equals("bm25")) {
      double k1,b,k2;
      k1=Double.parseDouble(parameters.get("BM25:k_1"));
      k2=Double.parseDouble(parameters.get("BM25:k_3"));
      b=Double.parseDouble(parameters.get("BM25:b"));

      model = new RetrievalModelBM25(k1,k2,b);
    }
    else if(modelString.equals("indri")) {

      if (parameters.containsKey("fb") && parameters.get("fb").equals("true")) {
        double mu, lambda,fbMU,fbWeight;
        int fbDocs,fbTerms;
        mu=Double.parseDouble(parameters.get("Indri:mu"));
        lambda=Double.parseDouble(parameters.get("Indri:lambda"));
        fbMU=Double.parseDouble(parameters.get("fbMu"));
        fbWeight=Double.parseDouble(parameters.get("fbOrigWeight"));
        fbTerms=Integer.parseInt(parameters.get("fbDocs"));
        fbDocs=Integer.parseInt(parameters.get("fbTerms"));
        model = new RetrievalModelIndri(lambda, mu, fbMU, fbWeight, fbDocs, fbTerms);

      } else {
        double mu, lambda;
        mu = Double.parseDouble(parameters.get("Indri:mu"));
        lambda = Double.parseDouble(parameters.get("Indri:lambda"));
        model = new RetrievalModelIndri(lambda, mu);
      }
    }
    else if(modelString.equals("letor")){

    }
    else {
      throw new IllegalArgumentException
        ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
    }
    return model;
  }

  /**
   * Print a message indicating the amount of memory used. The caller can
   * indicate whether garbage collection should be performed, which slows the
   * program but reduces memory usage.
   * 
   * @param gc
   *          If true, run the garbage collector before reporting.
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc)
      runtime.gc();

    System.out.println("Memory used:  "
        + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
  }

  /**
   * Process one query.
   * @param qString A string that contains a query.
   * @param model The retrieval model determines how matching and scoring is done.
   * @return Search results
   * @throws IOException Error accessing the index
   */
  static ScoreList processQuery(String qString, RetrievalModel model)
    throws IOException {

    String defaultOp = model.defaultQrySopName ();
    qString = defaultOp + "(" + qString + ")";
    Qry q = QryParser.getQuery (qString);

    // Show the query that is evaluated
    
    System.out.println("    --> " + q);
    
    if (q != null) {

      ScoreList r = new ScoreList ();
      
      if (q.args.size () > 0) {		// Ignore empty queries

        q.initialize (model);

        while (q.docIteratorHasMatch (model)) {
          int docid = q.docIteratorGetMatch ();
          double score = ((QrySop) q).getScore (model);
          r.add (docid, score);
          q.docIteratorAdvancePast (docid);
        }
      }

      return r;
    } else
      return null;
  }

  /**
   *  Process the query file.
   *  @param parameters
   *  @param model
   *  @throws IOException Error accessing the Lucene index.
   */
  static void processQueryFile(Map<String, String> parameters,
                               RetrievalModel model)
      throws IOException {

    BufferedReader input = null;

    try {
      String qLine = null;

      input = new BufferedReader(new FileReader(parameters.get("queryFilePath")));

      //  Each pass of the loop processes one query.

      while ((qLine = input.readLine()) != null) {
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
            ("Syntax error:  Missing ':' in query line.");
        }

        printMemoryUsage(false);

        String qid = qLine.substring(0, d);
        String query = qLine.substring(d + 1);

        System.out.println("Query " + qLine);
        ScoreList r = null;
        if(parameters.containsKey("fb")&& parameters.get("fb").equals("true")) {
          String updatedQuery;
          updatedQuery= expandQuery(parameters, query, qid, model);
          r=processQuery(updatedQuery,model);
        }
        else
        r = processQuery(query, model);
        r.sort();

        if (r != null) {
          printResults(parameters, r,qid);
         // System.out.println();
        }
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
    }
  }



  static String expandQuery(Map<String, String> parameters, String origQuery, String origQid,  RetrievalModel model) throws IOException {
    ScoreList r ,topDocs;
    int fbDocs,fbterms;
    String expanded_query;
    double fbMU ,fbOrigWeight;
    List<Map.Entry<String,Double>> sortedBag;
    HashMap<String, Double> BoW = new HashMap<>();
    HashMap<String, Long> word_frequencies = new HashMap<>();
    fbDocs=Integer.parseInt(parameters.get("fbDocs"));
    fbterms=Integer.parseInt(parameters.get("fbTerms"));
    fbMU = Double.parseDouble(parameters.get("fbMu"));
    fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
    r=retrieveDocuments(parameters,origQuery,origQid,model);
    topDocs=getTopDocs(r,fbDocs);
    collectWords(BoW,word_frequencies,topDocs);
    addScore(BoW,word_frequencies,topDocs,fbMU);
    sortedBag=getorderedBOW(BoW);
    String newQuery = buildNewQuery(fbterms,sortedBag);
    persistQuery(origQid,newQuery,parameters.get("fbExpansionQueryFile"));
    expanded_query = "#WAND ( " + fbOrigWeight + " #AND (" + origQuery + " ) " + (1 - fbOrigWeight) + " #WAND ( " + newQuery + " ) " + " ) ";
    return expanded_query;
  }


  static ScoreList retrieveDocuments(Map<String,String> parameters,String origQuery,String origQid,RetrievalModel model) throws IOException{
    ScoreList r = new ScoreList();
    if(parameters.containsKey("fbInitialRankingFile")) {
      String rankingFile = parameters.get("fbInitialRankingFile");
      try {
        BufferedReader reader = new BufferedReader(new FileReader(rankingFile));
        String docline;
        while((docline = reader.readLine()) != null) {
          String[] info = docline.split(" ");
          if (origQid.equals(info[0]))
            r.add(Idx.getInternalDocid(info[2]), Double.parseDouble(info[4]));
        }
        reader.close();
        r.sort();
        return r;
      } catch (Exception exp) {
        System.out.println("\nError while reading file from source");
      }
    }
    else {
        r = processQuery(origQuery, model);
        r.sort();
        return r;
    }
    return null;
  }
static ScoreList getTopDocs(ScoreList r,int fbDocs){

  ScoreList topDocs = new ScoreList();
  for (int i = 0; i < r.size() && i < fbDocs; i++) {
    topDocs.add(r.getDocid(i), r.getDocidScore(i));
  }
  return topDocs;
}

static void collectWords(HashMap<String, Double> BoW,HashMap<String, Long> word_frequencies,ScoreList topDocs ) throws IOException{
  for (int i = 0; i < topDocs.size(); i++) {
    TermVector termVector = new TermVector(topDocs.getDocid(i), "body");
    for(int j = 1; j < termVector.stemsLength(); j++) {
      String word = termVector.stemString(j);
      if (!termVector.stemString(j).contains(".") && !termVector.stemString(j).contains(",") && termVector.stemFreq(j)> 0) {
        BoW.put(word, 0.0);
        if (!word_frequencies.containsKey(word))
          word_frequencies.put(word, termVector.totalStemFreq(j));
      }
    }
  }
}

static void addScore(HashMap<String, Double> BoW,HashMap<String, Long> word_frequencies,ScoreList topDocs,double fbMU)throws IOException{
  double score,current_freq,tf,docLen,likelihood,curr_score,unnormalized_score;
  for (String word : BoW.keySet()) {
    score = 0.0;
    current_freq = (double) word_frequencies.get(word);
    for (int i = 0; i < topDocs.size(); i++) {
      TermVector termVector = new TermVector(topDocs.getDocid(i), "body");
      if(termVector.indexOfStem(word) != -1) {
        docLen = (double) Idx.getFieldLength("body", topDocs.getDocid(i));
        tf = (double) termVector.stemFreq(termVector.indexOfStem(word));
        likelihood = current_freq / Idx.getSumOfFieldLengths("body");
        unnormalized_score=tf + (fbMU * likelihood);
        curr_score = unnormalized_score / (docLen + fbMU);
        curr_score = curr_score * topDocs.getDocidScore(i) * Math.log(Idx.getSumOfFieldLengths("body") / current_freq);
        score += curr_score;
      }
      else {
        docLen = (double) Idx.getFieldLength("body", topDocs.getDocid(i));
        likelihood = current_freq / Idx.getSumOfFieldLengths("body");
        unnormalized_score=  fbMU * likelihood;
        curr_score = unnormalized_score / (docLen + fbMU);
        curr_score = curr_score * topDocs.getDocidScore(i) * Math.log(Idx.getSumOfFieldLengths("body") / current_freq);
        score += curr_score;
      }
    }
    BoW.put(word, score);
  }
}

static String buildNewQuery(int fbterms,List<Map.Entry<String,Double>> sortedBag){
  String query="";
  DecimalFormat df = new DecimalFormat("#.####");
  int startIndex;
  if(fbterms>sortedBag.size())
    startIndex=sortedBag.size();
  else
    startIndex=fbterms;
    startIndex--;
  while (startIndex >= 0) {
    query += df.format(sortedBag.get(startIndex).getValue()) + " "+sortedBag.get(startIndex).getKey() + " ";
    startIndex--;
  }
  return query;
}


static void persistQuery(String origQid,String newQuery,String fbExpansionQueryFile) throws IOException{
  String query_learnt = origQid + ": #WAND ( " + newQuery + " )\n";
  FileOutputStream out = new FileOutputStream(fbExpansionQueryFile, true);
  out.write(query_learnt.getBytes());
  out.close();

}
static List<Map.Entry<String,Double>> getorderedBOW(HashMap<String, Double> BoW){
    List<Map.Entry<String,Double>> sortedBag = new ArrayList<>(BoW.entrySet());
    Collections.sort(sortedBag,new Comparator<Map.Entry<String,Double>>() {
              @Override
              public int compare(Map.Entry<String,Double> e1, Map.Entry<String,Double> e2) {
                if(e1.getValue() < e2.getValue())
                  return 1;
                else if (e1.getValue() > e2.getValue())
                  return -1;
                return 0;
              }
            }
    );
    return sortedBag;

}


    /**
     * Print the query results.
     *
     * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
     * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
     *
     * QueryID Q0 DocID Rank Score RunID
     *
     * @param result
     *          A list of document ids and scores
     * @throws IOException Error accessing the Lucene index.
     */
  static void printResults(Map<String, String> parameters, ScoreList result,String qid) throws IOException {

    FileOutputStream output = new FileOutputStream(parameters.get("trecEvalOutputPath"), true);
    int trecEvalOutputLength = parameters.get("trecEvalOutputLength")==null ? 100 : Integer.parseInt(parameters.get("trecEvalOutputLength"));
    String dummyString=qid + " Q0 dummy 1 0 run-1\n";

    if (result.size() < 1) {
      output.write(dummyString.getBytes());
    }
    else {
      for (int i = 0;i < trecEvalOutputLength && i < result.size(); i++) {
        String log = qid +"\t"+ "Q0" +"\t"+ Idx.getExternalDocid(result.getDocid(i))
                + "\t"+ (i + 1)+ "\t"+ result.getDocidScore(i) +"\t"+ "run-1" + "\n";
        output.write(log.getBytes());
      }
    }
    output.close();
  }

  /**
   *  Given part of a query string, returns an array of terms with
   *  stopwords removed and the terms stemmed using the Krovetz
   *  stemmer.  Use this method to process raw query terms.
   *  @param query String containing query.
   *  @return Array of query tokens
   *  @throws IOException Error accessing the Lucene index.
   */
  public static String[] tokenizeQuery(String query) throws IOException {

    TokenStreamComponents comp = ANALYZER.createComponents ("dummyField");
    TokenStream tokenStream = ANALYZER.tokenStream ("dummyField", new StringReader(query));
    CharTermAttribute charTermAttribute =
            tokenStream.addAttribute(CharTermAttribute.class);
    tokenStream.reset();

    List<String> tokens = new ArrayList<String>();

    while (tokenStream.incrementToken()) {
      String term = charTermAttribute.toString();
      tokens.add(term);
    }

    tokenStream.close ();

    return tokens.toArray (new String[tokens.size()]);
  }

  /**
   *  Read the specified parameter file, and confirm that the required
   *  parameters are present.  The parameters are returned in a
   *  HashMap.  The caller (or its minions) are responsible for processing
   *  them.
   *  @return The parameters, in <key, value> format.
   */
  private static Map<String, String> readParameterFile (String parameterFileName)
    throws IOException {

    Map<String, String> parameters = new HashMap<String, String>();

    File parameterFile = new File (parameterFileName);

    if (! parameterFile.canRead ()) {
      throw new IllegalArgumentException
        ("Can't read " + parameterFileName);
    }

    Scanner scan = new Scanner(parameterFile);
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split ("=");
      parameters.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());

    scan.close();

    if (! (parameters.containsKey ("indexPath") &&
           parameters.containsKey ("queryFilePath") &&
           parameters.containsKey ("trecEvalOutputPath"))) {
      throw new IllegalArgumentException
        ("Required parameters were missing from the parameter file.");
    }

    return parameters;
  }

}
