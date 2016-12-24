package com.meshiest.eventnetwork.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.meshiest.eventnetwork.server.User;
import com.meshiest.eventnetwork.utils.Strings;

/**
 * The Client will provide an interface to the server and manage connections and
 * callbacks
 * 
 * @author Meshiest
 * @since 20161123
 * @version 0.1.3
 */
public class Client implements Runnable {

  /**
   * List of callbacks that are bound to this server (to run on the event
   * server)
   */
  private HashMap<String, Method> callbacks;

  /**
   * Client socket used for transmission
   */
  private Socket socket;

  /**
   * Host address of the server the client is connecting to
   */
  private String host;

  /**
   * Host port of the server the client is connecting to
   */
  private int port;

  /**
   * EventClient used for managing callbacks
   */
  private EventClient eventClient;

  /**
   * Reader for writing data from the server
   */
  private BufferedReader input;

  /**
   * Constructor that initializes the client and attempts to start a connection
   * 
   * @param host
   * @param port
   */
  public Client(String host, int port, EventClient eventClient) {
    this.host = host;
    this.port = port;

    this.callbacks = new HashMap<>();
    this.eventClient = eventClient;
    eventClient.bind(this);
    eventClient.init();
    this.reconnect();
  }

  /**
   * Reconnects the client to the server
   * 
   * @return true if the client connects
   */
  public boolean reconnect() {
    if (this.socket != null && this.socket.isConnected())
      return false;

    try {
      this.socket = new Socket(host, port);
      eventClient.onConnect();
      new Thread(this).start();
      return true;

    } catch (UnknownHostException e) {
      System.err.println("Failed to connect; unable to find host");
      eventClient.onConnectFail();
    } catch (IOException e) {
      System.err.println("Failed to connect; no server could be found");
      eventClient.onConnectFail();
    }
    return false;
  }

  public boolean send(String message) {
    if (socket.isClosed())
      return false;

    try {
      socket.getOutputStream().write(message.getBytes());
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  /**
   * Binds an action name to a callback that will be called
   * 
   * @param name
   *          Name of callback must be only [A-Za-z0-9_]
   * @param callback
   *          must be a method
   */
  public void addCallback(String name, Method callback) {
    if (callback.getReturnType() != void.class) {
      throw new IllegalArgumentException("Callback must have void return type");
    }

    callbacks.put(name, callback);
  }

  /**
   * Invokes a callback that was previously bound
   * 
   * @param name
   *          Name of callback
   * @param args
   *          Arguments to use on the callback
   */
  public boolean invokeCallback(String name, Object[] args) {
    // Argument doesn't exist
    if (!callbacks.containsKey(name))
      return false;

    try {
      if (args == null && callbacks.get(name).getParameterCount() != 0
          || args != null && callbacks.get(name).getParameterCount() != args.length) {
        System.err.println("Got wrong number of args for " + name);
        return false;
      }
      callbacks.get(name).invoke(eventClient, args);
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      System.err.println("Error in callback '" + name + "'");
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * Called when the user receives an unparsed message
   * 
   * @param message
   *          Message the user received
   */
  public void handleRawMessage(String message) {
    message = message.trim();

    Matcher matcher = Pattern.compile(User.MESSAGE_REGEX).matcher(message);
    if (!matcher.matches()) { // if the message doesn't comply with protocol
      eventClient.onRawMessage(message);
    } else {
      String name = matcher.group(1);
      String encodedArgs = matcher.group(3);
      boolean success;
      if (matcher.group(2) == null) {
        // message only has callback name and no arguments
        success = invokeCallback(name, new String[0]);
      } else {
        try {
          String decodedArgs = new String(Base64.getDecoder().decode(encodedArgs));
          Object[] parsedArgs = Strings.decodeMessage(decodedArgs);
          success = invokeCallback(name, parsedArgs);
        } catch (IllegalArgumentException e) {
          // message can't be base64 decoded
          success = false;
        }
      }
      if (!success) {
        // message couldn't be invoked
        eventClient.onRawMessage(message);
      }
    }

  }

  /**
   * Runnable method, keeps track of incoming messages
   */
  @Override
  public void run() {
    try {
      input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    } catch (IOException e) {
      e.printStackTrace();
    }

    while (socket.isConnected()) {
      String message = "";

      try {
        message = input.readLine();
      } catch (IOException e) {
        System.err.println("Server closed");
        try {
          socket.close();
          eventClient.onDisconnect();
          return;
        } catch (IOException e1) {
          e1.printStackTrace();
        }
        e.printStackTrace();
      }

      // Client wants to disconnect
      if (message == null) {
        try {
          eventClient.onDisconnect();
          socket.close();
          return;
        } catch (IOException e) {
          e.printStackTrace();
        }
        continue;
      }

      // interpret the message that was sent from the server
      handleRawMessage(message);

    }
  }

}
