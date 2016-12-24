package com.meshiest.eventnetwork.client;

import java.lang.reflect.Method;
import java.util.Base64;

import com.meshiest.eventnetwork.utils.Strings;

/**
 * This abstract class will be created to manage an event based connection to the server
 * @author Meshiest
 * @since 20161123
 * @version 0.1.1
 */
public abstract class EventClient {
  
  /**
   * Called after the EventClient is bound to the Client, used to initialize callbacks
   */
  public abstract void init();
  
  /**
   * Called when the client connects to the server
   */
  public abstract void onConnect();
  
  /**
   * Called when the client is unable to connect to the server 
   */
  public abstract void onConnectFail();
  
  /**
   * Called when the client disconnects from the server
   */
  public abstract void onDisconnect();
  
  /**
   * Called when a message can not be handled by the callback system
   * @param message Message the client sent
   */
  public abstract void onRawMessage(String message);
  
  /**
   * Client this eventclient is bound to
   */
  private Client client;
  
  /**
   * Binds a client to this object. This is done automatically by the client.
   * @param clietn client to bind
   */
  public void bind(Client client) {
    this.client = client;
  }
  
  /**
   * Binds a callback to the event
   * @param eventClient EventClient that has the callback
   * @param callbackName Name of callback to be added
   * @param methodName Name of method on the object 
   */
  public void on(EventClient eventClient, String callbackName, String methodName) {
    try {
      Method[] methods = eventClient.getClass().getMethods();
      Method method = null;
      for(int i = 0; i < methods.length; i++) {
        if(methods[i].getName() == methodName) {
          method = methods[i];
          break;
        }
      }
      if(method == null) {
        System.err.println("Could not find method '" + methodName + "'");
        return;
      }
      method.setAccessible(true); // allow other classes to access this (insecurely lol)
      client.addCallback(callbackName, method);
    } catch (SecurityException e) {
      System.err.println("Method '" + methodName + "' cannot be accessed");
    }
  }
    
  /**
   * Send a specific client a message
   * @param command Type of message
   * @param args Message parameters
   */
  public void emit(String command, Object ... args) {
    if(!command.matches("^[A-Za-z0-9_]+$"))
      throw new IllegalArgumentException("Command must match [A-Za-z0-9_]");
    String message = Strings.encodeMessage(args);
    message = Base64.getEncoder().encodeToString(message.getBytes());
    client.send(command + " " + message + "\n");
  }
  
  /**
   * Send only a command to a user
   * @param command Command to send
   */
  public void emit(String command) {
    if(!command.matches("^[A-Za-z0-9_]+$"))
      throw new IllegalArgumentException("Command must match [A-Za-z0-9_]");
    client.send(command + "\n");
  }
  

  
}
