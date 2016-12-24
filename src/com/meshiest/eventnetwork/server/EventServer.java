package com.meshiest.eventnetwork.server;

import java.lang.reflect.Method;
import java.util.Base64;

import com.meshiest.eventnetwork.utils.Strings;

/**
 * Interface that is the base of the event server
 * @author Meshiest
 * @since 20161121
 * @version 0.0.3
 *
 */
public abstract class EventServer {
  
  /**
   * Called after the EventServer is bound to the Server, used to initialize callbacks
   */
  public abstract void init();
  
  /**
   * Called when a client connects to the server
   * @param clientId Client's id
   */
  public abstract void onClientConnect(int clientId);
  
  /**
   * Called when a client disconnects from the server
   * @param clientId Client's id
   */
  public abstract void onClientDisconnect(int clientId);
  
  /**
   * Called when a message can not be handled by the callback system
   * @param clientId Client's id
   * @param message Message the client sent
   */
  public abstract void onRawMessage(int clientId, String message);
  
  /**
   * Server this eventserver is bound to
   */
  private Server server;
  
  /**
   * Binds a server to this object. This is done automatically by the server.
   * @param server server to bind
   */
  public void bind(Server server) {
    this.server = server;
  }
  
  /**
   * Gets the user name of the user
   * @param clientId
   * @return User's name
   */
  public String getUserName(int clientId) {
    if(!server.getUsers().containsKey(clientId))
      return "";
    return server.getUsers().get(clientId).getName();
  }
  
  /**
   * Sets the user name of the user
   * @param clientId
   * @return User's name
   */
  public void setUserName(int clientId, String name) {
    if(server.getUsers().containsKey(clientId))
      server.setUserName(clientId, name);;
  }
  
  /**
   * Binds a callback to the event
   * @param eventServer EventServer that has the callback
   * @param callbackName Name of callback to be added
   * @param methodName Name of method on the object 
   */
  public void on(EventServer eventServer, String callbackName, String methodName) {
      try {
        Method[] methods = eventServer.getClass().getMethods();
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
        server.addCallback(callbackName, method);
      } catch (SecurityException e) {
        System.err.println("Method '" + methodName + "' cannot be accessed");
      }
  }
  
  /**
   * Logs to the server log
   * @param message Message to log
   */
  public void log(String message) {
    server.logln("server", message);
  }
  
  /**
   * Send a specific client a message
   * @param userId Client to send to
   * @param command Type of message
   * @param args Message parameters
   */
  public void emit(int userId, String command, Object ... args) {
    if(!command.matches("^[A-Za-z0-9_]+$"))
      throw new IllegalArgumentException("Command must match [A-Za-z0-9_]");
    String message = Strings.encodeMessage(args);
    message = Base64.getEncoder().encodeToString(message.getBytes());
    server.sendToClient(userId, command + " " + message + "\n");
  }
  
  /**
   * Send only a command to a user
   * @param userId User's id
   * @param command Command to send
   */
  public void emit(int userId, String command) {
    if(!command.matches("^[A-Za-z0-9_]+$"))
      throw new IllegalArgumentException("Command must match [A-Za-z0-9_]");
    server.sendToClient(userId, command + "\n");
  }
  
  /**
   * Sends every client a message
   * @param command Type of message
   * @param args Message parameters
   */
  public void broadcast(String command, Object ... args) {
    if(!command.matches("^[A-Za-z0-9_]+$"))
      throw new IllegalArgumentException("Command must match [A-Za-z0-9_]");
    String message = Strings.encodeMessage(args);
    message = Base64.getEncoder().encodeToString(message.getBytes());
    server.sendToClients(command + " " + message + "\n");
  }
  
  /**
   * Sends every client a command
   * @param command Command name
   */
  public void broadcast(String command) {
    if(!command.matches("^[A-Za-z0-9_]+$"))
      throw new IllegalArgumentException("Command must match [A-Za-z0-9_]");
    server.sendToClients(command + "\n");
  }
  
  /**
   * Sends all but one client a message
   * @param id Id of user to ignore
   * @param command Type of message
   * @param args Message parameters
   */
  public void broadcast(int id, String command, Object ... args) {
    if(!command.matches("^[A-Za-z0-9_]+$"))
      throw new IllegalArgumentException("Command must match [A-Za-z0-9_]");
    String message = Strings.encodeMessage(args);
    message = Base64.getEncoder().encodeToString(message.getBytes());
    server.sendToClients(id, command + " " + message + "\n");
  }
  
  /**
   * Sends all but one client a command
   * @param id Id of user to ignore
   * @param command Command name
   */
  public void broadcast(int id, String command) {
    if(!command.matches("^[A-Za-z0-9_]+$"))
      throw new IllegalArgumentException("Command must match [A-Za-z0-9_]");
    server.sendToClients(id, command + "\n");
  }

  
}
