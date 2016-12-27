package com.meshiest.eventnetwork.utils;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class for encoding and decoding json strings and the like
 * @author Meshiest
 * @since 20161123
 * @version 0.1.3
 *
 */
public class Strings {

  /**
   * Escapes a string with backslashes where necessary
   * @param string String to escape
   * @return String with escaped components
   */
  public static String escapeString(String string) {
    return string
        .replaceAll("\"", "\\\\\"")
        .replaceAll("\\\\", "\\\\\\\\") // oh goodness
        .replaceAll("\f", "\\\\f")
        .replaceAll("\n", "\\\\n")
        .replaceAll("\r", "\\\\r")
        .replaceAll("\t", "\\\\t");

  }
  
  /**
   * Removes unescaped backslashes in a string and replaces with the intended character
   * @param string String to unescape
   * @return Unescaped string
   */
  public static String unescapeString(String string) {
    return string
        .replaceAll("\\\\\"", "\"")
        .replaceAll("\\\\\\\\", "\\\\")
        .replaceAll("\\\\f", "\f")
        .replaceAll("\\\\n", "\n")
        .replaceAll("\\\\r", "\r")
        .replaceAll("\\\\t", "\t");
  }
  
  
  /**
   * Parses the arguments that should be in the form of a JSON list
   * @param message JSON Array string
   * @return list of objects in the json string
   */
  public static Object[] decodeMessage(String message) {
    // Doesn't support unicode because... this isn't for emoji lol
    // oh goodness the backslashes, it's double escaped! So meta!
    String jsonStringRegex = "(?<!\\\\)\"(([^\"\\\\]|\\\\[\"\\\\/fnrt])+)\"";
    Pattern stringPattern = Pattern.compile(jsonStringRegex);

    String jsonBooleanRegex = "(false|true)";
    Pattern booleanPattern = Pattern.compile(jsonBooleanRegex);

    String jsonNumberRegex = "(-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]\\d+)?)";
    Pattern numberPattern = Pattern.compile(jsonNumberRegex);
    
    String jsonNullRegex = "(null)";
    Pattern nullPattern = Pattern.compile(jsonNullRegex);

    String jsonArrayRegex = "(\\[(.+?,? *)+\\])";
    Pattern arrayPattern = Pattern.compile(jsonArrayRegex);

    String jsonTypeBlob = "((" + jsonArrayRegex + ")|(" + jsonStringRegex + ")|(" +
        jsonBooleanRegex + ")|(" + jsonNumberRegex + ")|(" +
        jsonNullRegex + "))";
    
    Matcher m = Pattern.compile("^\\[((" + jsonTypeBlob + ",? *)+)\\]$").matcher(message);
    // Check if the whole message matches JSON array format
    if(!m.matches())
      return null;
    
    Matcher typeMatcher = Pattern.compile(jsonTypeBlob).matcher(m.group(1));

    ArrayList<Object> matches = new ArrayList<Object>();
    // add all the matches to a group
    while(typeMatcher.find()) {
      String obj = typeMatcher.group(1);
      Matcher stringMatcher = stringPattern.matcher(obj); 
      Matcher numberMatcher = numberPattern.matcher(obj); 
      Matcher booleanMatcher = booleanPattern.matcher(obj);
      Matcher nullMatcher = nullPattern.matcher(obj);
      Matcher arrayMatcher = arrayPattern.matcher(obj);
      
      if(stringMatcher.matches())
        matches.add(unescapeString(stringMatcher.group(1)));
      
      else if(booleanMatcher.matches()) {
        matches.add(booleanMatcher.group(1).equals("true"));
      }
      
      else if(numberMatcher.matches()) {
        try {
          // try to parse it as an integer
          matches.add(Integer.parseInt(numberMatcher.group(1)));
        } catch (NumberFormatException e) {
          try {
            // try to parse it as a double
            matches.add(Double.parseDouble(numberMatcher.group(1)));
          } catch (NumberFormatException e2){
            System.err.println("Could not parse number '" + numberMatcher.group(1) +"'");
          }
        }
      } else if (nullMatcher.matches()) {
        matches.add(null);
        
      } else if(arrayMatcher.matches()) {
        // recursive array decoding
        matches.add(decodeMessage(arrayMatcher.group(1)));
      }
      
    }
    Object[] objArray = new Object[matches.size()];
    // Convert the ArrayList to an array
    for(int i = 0; i < objArray.length; i++)
      objArray[i] = matches.get(i);
    
    return objArray;
  }
    
  /**
   * Encodes a string array into a JSON Array
   * @param args Object[] to encode (accepts arrays, int, double, string, boolean, and null)
   * @return Encoded String
   */
  public static String encodeMessage(Object[] args) {
    if(args == null)
      return "[]";
    String params = "[";
    for (int i = 0; i < args.length; i++) {
      if (i != 0)
        params += ",";
      Object arg = args[i];
      
      // null handling (no type)
      if (arg == null)
        params += "null";
      
      // recursive nested array handling
      else if (arg instanceof Object[])
        params += encodeMessage((Object[]) arg);
      
      // integer handling
      else if (arg instanceof Integer)
        params += (int)arg;
      
      // double handling
      else if (arg instanceof Double)
        params += (double)arg;
      
      // string handling
      else if (arg instanceof String)
        params += "\"" + escapeString((String)arg) + "\"";

      // boolean handling
      else if (arg instanceof Boolean)
        params += (boolean) arg;
      
      else {
        System.err.println("Could not encode '" + arg + "'");
        return null;
      }
    }
    return params + "]";
  }
}
