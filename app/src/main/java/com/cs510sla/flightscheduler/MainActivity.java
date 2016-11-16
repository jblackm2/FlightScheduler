package com.cs510sla.flightscheduler;

import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import ai.api.AIConfiguration;
import ai.api.AIDataService;
import ai.api.AIListener;
import ai.api.AIService;
import ai.api.AIServiceException;
import ai.api.model.AIError;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Result;
import com.google.gson.JsonElement;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class MainActivity extends AppCompatActivity implements AIListener {

    private Button listenButton;
    private Button queryButton;
    private TextView resultTextView;
    private TextToSpeech tts;
    private EditText queryText;

    private AIService aiService;

    private static final int AIRLINE = 0;
    private static final int FLIGHT_NUMBER = 1;
    private static final int DEPARTURE_CITY = 2;
    private static final int DEPARTURE_TIME = 3;
    private static final int ARRIVAL_CITY = 4;
    private static final int ARRIVAL_TIME = 5;
    private static final int STATUS = 6;

    AIDataService aiDataService;
    AIRequest aiRequest;

    private String[] paramArray = {"Airline", "FlightNumber", "DepartureCity", "DepartureTime", "ArrivalCity", "ArrivalTime", "status"};

    private  String[][] table =
            {{"Airline", "FlightNumber", "DepartureCity", "DepartureTime", "ArrivalCity", "ArrivalTime", "status"},
                    {"AjaxAir", "113", "Portland", "8:03 AM", "Atlanta", "12:51 PM", "landed"},
                    {"AjaxAir", "114", "Atlanta", "2:05 PM", "Portland", "4:44 PM", "boarding"},
                    {"BakerAir", "121", "Atlanta", "5:14 PM", "New York", "7:20 PM", "departed"},
                    {"BakerAir", "122", "New York", "9:00 PM", "Portland", "12:13 AM", "scheduled"},
                    {"BakerAir", "124", "Portland", "9:03 AM", "Atlanta", "12:52 PM", "delayed to 9:55"},
                    {"CarsonAir", "522", "Portland", "2:15 PM", "New York", "4:58 PM", "scheduled"},
                    {"CarsonAir", "679", "New York", "9:30 AM", "Atlanta", "11:30 AM", "departed"},
                    {"CarsonAir", "670", "New York", "9:30 AM", "Portland", "12:05 PM", "departed"},
                    {"CarsonAir", "671", "Atlanta", "1:20 PM", "New York", "2:55 PM", "scheduled"},
                    {"CarsonAir", "672", "Portland", "1:25 PM", "New York", "8:36 PM", "scheduled"}};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        listenButton = (Button) findViewById(R.id.speakButton);
        resultTextView = (TextView) findViewById(R.id.resultText);
        queryButton = (Button) findViewById(R.id.typedButton);
        queryText = (EditText) findViewById(R.id.writtenQuery);

        final AIConfiguration config = new AIConfiguration(getString(R.string.clientToken),
                AIConfiguration.SupportedLanguages.English,
                AIConfiguration.RecognitionEngine.System);

        aiService = AIService.getService(this, config);
        aiService.setListener(this);

         aiDataService = new AIDataService(this, config);
         aiRequest = new AIRequest();

        tts = new TextToSpeech(MainActivity.this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int code) {
                if (code == TextToSpeech.SUCCESS){
                    tts.setLanguage(Locale.US);
                }
                //Greeting message
                convertTTS("Welcome to Flight Scheduler. You can choose to either say your query, or type it.");
            }
        });

    }

    private void convertTTS(String text){
        if (text.length() > 0){
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void listenButtonOnClick(final View view) {
        convertTTS(getString(R.string.questionPrompt));

        while(tts.isSpeaking()){
            //no-op
        }
        aiService.startListening();
    }

    public void queryButtonOnClick(final View view) throws AIServiceException {

        String text = String.valueOf(queryText.getText());
        if(text !=null && !text.isEmpty()){
            aiRequest.setQuery(text);
        }

        new AsyncTask<AIRequest, Void, AIResponse>() {
            @Override
            protected AIResponse doInBackground(AIRequest... requests) {
                final AIRequest request = requests[0];
                try {
                    final AIResponse response = aiDataService.request(aiRequest);
                    return response;
                } catch (AIServiceException e) {
                }
                return null;
            }
            @Override
            protected void onPostExecute(AIResponse aiResponse) {
                if (aiResponse != null) {
                    onResult(aiResponse);
                }
            }
        }.execute(aiRequest);
    }

    @Override
    public void onResult(AIResponse response) {
        Result result = response.getResult();

        if(result.getAction() != null && !result.getAction().isEmpty()) {
            parseAction(result);
        }
        // Get parameters
        String parameterString = "";
        if (result.getParameters() != null && !result.getParameters().isEmpty()) {

            for (final Map.Entry<String, JsonElement> entry : result.getParameters().entrySet()) {
                parameterString += "(" + entry.getKey() + ", " + entry.getValue() + ") ";
            }
        }
    }

    private void parseAction(Result result) {

        String action = result.getAction();

        if (action.equals("flight_number")) {
            parseFlightNumber(result);
        }
        else if(action.equals("airlines")){
            parseAirline(result);
        }
        else if(action.equals("status")){
            parseFlightStatus(result);
        }
        else if(action.equals("departures_city")){
            parseDeparturesCity(result);
        }
        else if(action.equals("arrivals_city")){
            parseArrivalsCity(result);
        }
        else if(action.equals("departure_time")){
            parseDeparturesTime(result);
        }
        else if(action.equals("arrival_time")){
            parseArrivalsTime(result);
        }
        else if(action.equals("next")){
            parseNext(result);
        }

    }


    //Method to get parmeter from the API.ai response, and determine which column to search in
    private Map<String, String> getParams(Result result) {
        String searchParam = null;
        String searchCol = null;
        Map<String,String> searchMap = new HashMap<>();

        for(int i = 0; i < paramArray.length; i++){
            if(result.getParameters().get(paramArray[i]) != null){
                searchParam = result.getParameters().get(paramArray[i]).toString();
                //Weird issue with an extra "" being in the String
                searchParam = searchParam.substring(1,searchParam.length()-1);
                searchCol = paramArray[i];
                //searchMap.put("searchCol", searchCol);
                //searchMap.put("searchParam", searchParam);
                searchMap.put(searchCol, searchParam);
            }
        }
        return searchMap;
    }

    private Set<String> filterTime(String now, Set results){
        Set<String> resSet = new HashSet();
        Object[] res = results.toArray();
        for(int i = 0; i < res.length; i++){
            if(now.compareTo(res[i].toString()) < 0){
                resSet.add(res[i].toString());
            }
        }
        return resSet;
    }
    private void parseNext(Result result) {
        Set<String> resSet = new HashSet();
        Map<String, String> resMap = getParams(result);
        Calendar nowTime = Calendar.getInstance();
        String amPM;
        int nowHour = nowTime.get(Calendar.HOUR_OF_DAY);
        if(nowHour < 12){
            amPM = "AM";
        }
        else{
            amPM = "PM";
        }
        String nowStr = nowTime.get(Calendar.HOUR) + ":" + nowTime.get(Calendar.MINUTE) + " " + amPM;

        if (!resMap.isEmpty()) {
            if(resMap.containsKey("DepartureCity")){
                resSet = locateInTable(resMap, DEPARTURE_TIME);
                resSet = filterTime(nowStr, resSet);
            }
            else{
                resSet = locateInTable(resMap, ARRIVAL_TIME);
                resSet = filterTime(nowStr, resSet);
            }

            if (resSet.size() == 0) {
                noResultsError(result.getResolvedQuery());
            }
            else{
                showResults("You asked: " + result.getResolvedQuery() + "?" +
                        "\nThe flights that match your query are: " + resSet.toString());
            }
        }
        else{
            noParamError();
        }

    }

    private void parseDeparturesCity(Result result) {
        Set<String> resSet = new HashSet();
        Map<String, String> resMap = getParams(result);

        if (!resMap.isEmpty()) {

            resSet = locateInTable(resMap, DEPARTURE_CITY);
            if (resSet.size() == 0) {
                noResultsError(result.getResolvedQuery());
            }
            else{
                showResults("You asked: " + result.getResolvedQuery() + "?" +
                        "\nThe cities that match your query are: " + resSet.toString());
            }
        }
        else{
            noParamError();
        }
    }

    private void parseDeparturesTime(Result result) {
        Set<String> resSet = new HashSet();
        Map<String, String> resMap = getParams(result);

        if (!resMap.isEmpty()) {
            resSet = locateInTable(resMap, DEPARTURE_TIME);
            if (resSet.size() == 0) {
                noResultsError(result.getResolvedQuery());
            }
            else{
                showResults("You asked: " + result.getResolvedQuery() + "?" +
                        "\nThe flight departure time that matches your query is: " + resSet.toString());
            }
        }
        else{
            noParamError();
        }
    }

    private void parseArrivalsTime(Result result) {
        Set<String> resSet = new HashSet();
        Map<String, String> resMap = getParams(result);

        if (!resMap.isEmpty()) {
            resSet = locateInTable(resMap, ARRIVAL_TIME);
            if (resSet.size() == 0) {
                noResultsError(result.getResolvedQuery());
            }
            else{
                showResults("You asked: " + result.getResolvedQuery() + "?" +
                        "\nThe flight arrival time that matches your query is: " + resSet.toString());
            }
        }
        else{
            noParamError();
        }
    }

    private void parseFlightStatus(Result result) {
        Set<String> resSet = new HashSet();
        Map<String, String> resMap = getParams(result);
        String boolAnswer;

        if (!resMap.isEmpty()) {
            //If a status param is included, we know we want to compare a column to it
            if(resMap.containsKey(paramArray[STATUS])){
                boolAnswer = resMap.get(paramArray[STATUS]);
                resMap.remove(paramArray[STATUS]);
                resSet = locateInTable(resMap, STATUS);
                //If the provided param and matches the result in table
                if(resSet.contains(boolAnswer)){//Kind of a hack
                    showResults("You asked: " + result.getResolvedQuery() + "?" +
                            "\nThe result of the query is: true");
                }
                else{
                    showResults("You asked: " + result.getResolvedQuery() + "?" +
                            "\nThe result of the query is: false");
                }
            }
            else{
                resSet = locateInTable(resMap, STATUS);
                if (resSet.size() == 0) {
                    noResultsError(result.getResolvedQuery());
                }
                else{
                    showResults("You asked: " + result.getResolvedQuery() + "?" +
                            "\nThe flight status that matches your query are: " + resSet.toString());
                }
            }
        }
        else{
            noParamError();
        }
    }

    private void parseArrivalsCity(Result result) {
        Set<String> resSet = new HashSet();
        Map<String, String> resMap = getParams(result);

        if (!resMap.isEmpty()) {
            resSet = locateInTable(resMap, ARRIVAL_CITY);
            if (resSet.size() == 0) {
                noResultsError(result.getResolvedQuery());
            }
            else{
                showResults("You asked: " + result.getResolvedQuery() + "?" +
                        "\nThe arrival cities that match your query are: " + resSet.toString());
            }
        }
        else{
            noParamError();
        }
    }

    private void parseAirline(Result result) {
        Set<String> resSet = new HashSet();
        Map<String, String> resMap = getParams(result);

        if (!resMap.isEmpty()) {
            resSet = locateInTable(resMap, AIRLINE);
            if (resSet.size() == 0) {
                noResultsError(result.getResolvedQuery());
            }
            else{
                showResults("You asked: " + result.getResolvedQuery() + "?" +
                        "\nThe airlines that match your query are: " + resSet.toString());
            }
        }
        else{
            noParamError();
        }
    }

    private void parseFlightNumber(Result result) {
        Set<String> resSet = new HashSet();
        Map<String, String> resMap = getParams(result);

        if (!resMap.isEmpty()) {
            resSet = locateInTable(resMap, FLIGHT_NUMBER);
            if (resSet.size() == 0) {
                noResultsError(result.getResolvedQuery());
            }
            else{
                showResults("You asked: " + result.getResolvedQuery() + "?" +
                        "\nThe flight numbers that match your query are: " + resSet.toString());
            }
        }
        else{
            noParamError();
        }

    }

    private Set<String> locateInTable(Map<String, String> map, int colToGetResultsFrom) {
        Set<String> resSet = new HashSet<>();
        int columnMatch = 0;
        ArrayList<Set<String>> setArrayList = new ArrayList<>();
        Set<String> test = new HashSet<>();


        for ( Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();

            for (int i = 0; i < 7; i++) {
                if (table[0][i].contains(key)) {
                    columnMatch = i;
                    break;
                }
            }
            //Multi-parameter queries
            for (int i = 0; i < 11; i++) {
                String matchCheck = table[i][colToGetResultsFrom];
                if (table[i][columnMatch].contains(val)) {

                    if(map.size() > 1){
                        if(resSet.contains(matchCheck)){
                            test.add(matchCheck);
                        }
                    }
                    resSet.add(matchCheck);
                }
            }

            //setArrayList.add(resSet);
        }
        if(!test.isEmpty()) {
            resSet = test;
        }
        return resSet;

        /*int columnMatch = 0;

        //finds the column for the data we need
        for (int i = 0; i < 7; i++){
            if (table[0][i].contains(searchColumn)) {
                columnMatch = i;
                break;
            }
            else{
                //TODO:return some error
            }
        }
        for (int i = 0; i < 11; i++) {
            if (table[i][columnMatch].contains(itemToSearchFor)) {
                resSet.add(table[i][colToGetResultsFrom]);
            }
        }

        //This should be the data we need to finish the response to the user
        return resSet;*/
    }

    private void noParamError(){
        showResults("No parameters included in request.");
    }

    private void noResultsError(String query) {
        showResults("Your query " + query + " has returned no results");
    }

    private void showResults(String response) {
        resultTextView.setText(response);
        convertTTS(response);
    }

    @Override
    public void onError(AIError error) {
        showResults(error.toString());
        aiService.setListener(this);

    }

    @Override
    public void onAudioLevel(float level) {

    }

    @Override
    public void onListeningStarted() {

    }

    @Override
    public void onListeningCanceled() {

    }

    @Override
    public void onListeningFinished() {

    }
}
