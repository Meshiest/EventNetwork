package com.meshiest.eventnetwork.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.meshiest.eventnetwork.utils.Strings;

/**
 * A class to contain a networked user
 * @author Meshiest
 * @since 20161121
 * @version 0.0.4
 */
public class User implements Runnable {
  
  /**
   * Number of bytes for input to read at a time
   */
  public static final int BUFFER_SIZE = 1024;

  /**
   * Regex used for testing validity of user messages 
   */
  public static final String MESSAGE_REGEX = "^([A-Za-z0-9_]+)( ([A-Za-z0-9+/=]+))?$";
    
  /**
   * Pattern generated to create matchers
   */
  public static final Pattern MESSAGE_PATTERN = Pattern.compile(MESSAGE_REGEX);
  
  /**
   * Socket client uses to communicate
   */
  private Socket socket;
  
  /**
   * Server that manages this client
   */
  private Server server;
  
  /**
   * Writer for sending data to the client
   */
  private OutputStream output;
  
  /**
   * Reader for writing data from the client
   */
  private BufferedReader input;
  
  /**
   * Id given to this user
   */
  private int id;
  
  /**
   * User name, defaults to "User [ID]"
   * Used in the toString method, displayed in the user list
   */
  private String name;
  
  /**
   * Base constructor for creating a new client
   * @param socket Socket the client uses to communicate with
   * @param server Server that manages clients
   * @throws IOException 
   */
  public User(Socket socket, Server server, int id) throws IOException {
    this.socket = socket;
    this.server = server;
    this.id = id;
    this.name = "User " + id;
    
    output = socket.getOutputStream();
    input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
  }
  
  /**
   * Gets the user's id
   * @return the user's id
   */
  public int getId() { 
    return this.id;
  }
  
  /**
   * Update the user's display name
   * @param name The user's display name
   */
  public void setName(String name) {
    this.name = name;
  }
  
  /**
   * The user's display name
   * @return The user's display name
   */
  public String getName() {
    return this.name;
  }
  
  /**
   * Called when the user receives an unparsed message
   * @param message Message the user received
   */
  public void handleRawMessage(String message) {
    message = message.trim();
    Matcher matcher = Pattern.compile(MESSAGE_REGEX).matcher(message);
    if(!matcher.matches()) { // if the message doesn't comply with protocol
      server.getEventServer().onRawMessage(id, message);
    } else {
      String name = matcher.group(1);
      String encodedArgs = matcher.group(3);
      boolean success;
      if(matcher.group(2) == null) {
        // message only has callback name and no arguments
        success = server.invokeCallback(name, id, new String[0]);
      } else {
        try {
          String decodedArgs = new String(Base64.getDecoder().decode(encodedArgs));
          Object[] parsedArgs = Strings.decodeMessage(decodedArgs);
          success = server.invokeCallback(name, id, parsedArgs);
        } catch (IllegalArgumentException e) {
          // message can't be base64 decoded
          success = false;
        }
      }
      if(!success) {
        // message couldn't be invoked
        server.getEventServer().onRawMessage(id, message);
      }
    }
    
  }
  
  /**
   * Send a message to the client
   * @param Message message to send to the client
   * @return whether or not the message was properly sent
   */
  public boolean write(String message) {
    if(socket.isClosed())
      return false;
    
    try {
      output.write(message.getBytes());
      return true;
    } catch (Exception e) {
      return false;
    }
  }
  
  public void remove(){
    try {
      socket.close();
      server.removeUser(this);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Called when thread is created. Handles all input from the user
   */
  @Override
  public void run() {
    while(socket.isConnected()) {
      String message = "";
      
      try {
        message = input.readLine();
      } catch (IOException e) {
        System.err.println("Connection reset on user " + id);
        remove();
        return;
      }
      
      // Client wants to disconnect
      if(message == null) {
        remove();
        return;
      }
      
      handleRawMessage(message);
      
    } 
  }
  
  /**
   * @see Object#toString()
   */
  public String toString() {
    return name + " #" + id;
  }
  
}
