package com.meshiest.eventnetwork.server;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 * This is the main class for a generic Game Server
 * @author Meshiest
 * @since 20161121
 * @version 0.1.12
 */
@SuppressWarnings("serial")
public class Server extends JFrame implements ActionListener, Runnable  {
    
  /**
   * Text area used to display logs
   */
  private JTextArea logTextArea;
  
  /**
   * List of users
   */
  private HashMap<Integer, User> users;
  
  /**
   * List of callbacks that are bound to this server (to run on the event server)
   */
  private HashMap<String, Method> callbacks;
  
  /**
   * Server socket used for transmission
   */
  private ServerSocket socket;
  
  /**
   * EventServer this server is talking to directly
   */
  private EventServer eventServer;
  
  /**
   * Last id to be assigned to a user
   */
  private int ids;
 
  /**
   * Button on the action panel used for stopping the server 
   */
  private JButton stopServerButton;
  
  /**
   * Button on the list action panel to rename a user;
   */
  private JButton renameUserButton;
  
  /**
   * Button on the list action panel to remove a user;
   */
  private JButton removeUserButton;
  
  /**
   * List of users displayed on the right
   */
  private ListModel<User> userListModel;

  /**
   * JList of users
   * {@link Server#userListModel}
   */
  private JList<User> userList;
  
  /**
   * ListDataListeners that will be updated when users join or leave
   */
  private ArrayList<ListDataListener> dataListeners;
  
  /**
   * Graph that will show the number of requests per second
   */
  private RequestInfoPanel requestInfoPanel;
  
  /**
   * Default constructor, creates generic server interface
   * @param port Port to host the server on
   * @throws IOException 
   */
  public Server(int port, EventServer eventServer) {
    super("Server on " + port);
    setSize(800, 600);
    setResizable(true);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    
    this.eventServer = eventServer;
    this.users = new HashMap<>();
    this.callbacks = new HashMap<>();
    this.dataListeners = new ArrayList<>();
    
    JPanel contentPane = new JPanel(new BorderLayout());
    
    logTextArea = new JTextArea();
    logTextArea.setEditable(false);
    contentPane.add(new JScrollPane(logTextArea), BorderLayout.CENTER);
    
    requestInfoPanel = new RequestInfoPanel();
    contentPane.add(requestInfoPanel, BorderLayout.NORTH);
    
    JPanel listPanel = new JPanel(new BorderLayout());
    
    userListModel = new ListModel<User>() {
      @Override
      public User getElementAt(int index) {
        return (User) getUsers().values().toArray()[index];
      }

      @Override
      public int getSize() {
        return getUsers().size();
      }

      @Override
      public void addListDataListener(ListDataListener l) {
        dataListeners.add(l);
      }

      @Override
      public void removeListDataListener(ListDataListener l) {
        dataListeners.remove(l);
      }
    };
    
    userList = new JList<>(userListModel);
    userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    userList.setLayoutOrientation(JList.VERTICAL);
    userList.setVisibleRowCount(-1);
    
    JScrollPane listScrollPane = new JScrollPane(userList);
    // assign a fixed width of the user list
    Dimension d = userList.getPreferredSize();
    d.width = 200;
    listScrollPane.setPreferredSize(d);
    
    listPanel.add(listScrollPane, BorderLayout.CENTER);
    
    JPanel listActionPanel = new JPanel(new FlowLayout());
    
    renameUserButton = new JButton("Rename");
    renameUserButton.addActionListener(this);
    listActionPanel.add(renameUserButton);

    removeUserButton = new JButton("Kick");
    removeUserButton.addActionListener(this);
    listActionPanel.add(removeUserButton);

    
    listPanel.add(listActionPanel, BorderLayout.SOUTH);
    
    contentPane.add(listPanel, BorderLayout.EAST);
    
    JPanel actionPanel = new JPanel(new FlowLayout());
    
    stopServerButton = new JButton("Stop Server");
    stopServerButton.addActionListener(this);
    actionPanel.add(stopServerButton);
    
    contentPane.add(actionPanel, BorderLayout.SOUTH);
    
    setContentPane(contentPane);
    setVisible(true);
    
    logln("info", "Initializing server");
    eventServer.bind(this);
    eventServer.init();
    try {
      this.socket = new ServerSocket(port);
      logln("info", "Starting server on port "+ port);
      new Thread(this).start();
    } catch (IOException e) {
      System.err.println("Could not initialize server, port may be in use.");
      System.exit(1);
    }
  }
  
  /**
   * Get a map of all of the connected users
   * @return a map of all of the connected users
   */
  public HashMap<Integer, User> getUsers() { 
    return this.users;
  }
  
  /**
   * Binds an action name to a callback that will be called 
   * @param name Name of callback must be only [A-Za-z0-9_]
   * @param callback must be a method(int, String[])
   */
  public void addCallback(String name, Method callback) {
    if(callback.getReturnType() != void.class) {
      throw new IllegalArgumentException("Callback must have void return type");
    }
    if(callback.getParameterCount() < 1) {
      throw new IllegalArgumentException("Callback requires at least one parameter");
    }
    
    Class<?>[] types = callback.getParameterTypes();
    
    if(types[0] != int.class) {
      throw new IllegalArgumentException("Callback requires first parameter to be int");
    }
    
    callbacks.put(name, callback);
  }
  
  /**
   * Invokes a callback that was previously bound
   * @param name Name of callback
   * @param args Arguments to use on the callback
   */
  public boolean invokeCallback(String name, int userId, Object[] args) {
    // increment the number of requests per second
    requestInfoPanel.inc();
    
    // Argument doesn't exist
    if(!callbacks.containsKey(name))
      return false;
    
    try {
      if(args == null && callbacks.get(name).getParameterCount() != 1) {
        System.err.println("Got wrong number of args for " + name);
        return false;
      }

      if(args == null)
        callbacks.get(name).invoke(eventServer, userId);
      else {
        Object[] params = new Object[args.length + 1];
        params[0] = userId;
        
        for(int i = 0; i < args.length; i++)
          params[i+1] = args[i];
        
        if(callbacks.get(name).getParameterCount() != params.length) {
          System.err.println("Got wrong number of args for " + name + " (user " + userId + ")");
          return false;
        }
        
        // Have to use an object array here so the code knows to use the array as the arguments
        callbacks.get(name).invoke(eventServer, params);
      }
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {      
      e.printStackTrace();
      return false;
    }
    return true;
  }
  
  /**
   * Updates a user's display name
   * @param userId User's id
   * @param name New name
   */
  public void setUserName(int userId, String name) {
    if(!users.containsKey(userId))
      return;
    
    User user = users.get(userId);
    
    user.setName(name);
    int index = new ArrayList<Integer>(users.keySet()).indexOf(user.getId());
    ListDataEvent listDataEvent = new ListDataEvent(user, ListDataEvent.CONTENTS_CHANGED, index, index);
    for(ListDataListener l : dataListeners)
      l.contentsChanged(listDataEvent);
  }
  
  /**
   * Sends a direct message to a client
   * @param userId Client's id
   * @param message Message to send
   */
  public void sendToClient(int userId, String message) {
    if(users.containsKey(userId))
      users.get(userId).write(message);
  }
  
  /**
   * Broadcast a message to all clients
   * @param message Message to send
   */
  public synchronized void sendToClients(String message) {
    for(User user : users.values())
      user.write(message);
  }

  /**
   * Broadcast a message to all but one client
   * @param id User to ignore
   * @param message Message to send
   */
  public synchronized void sendToClients(int id, String message) {
    for(User user : users.values())
      if(user.getId() != id)
        user.write(message);
  }

  /**
   * Logs a message onto the log text area
   * @param tag Tag to flag the message with
   * @param message message to log
   */
  public void log(String tag, String message) {
    logTextArea.append("[" + tag.toUpperCase() + "] " + message);
    logTextArea.setCaretPosition(logTextArea.getText().length());
  }
  
  /**
   * Logs a message and appends a new line at the end
   * @param tag Tag to flag the message with
   * @param message
   */
  public void logln(String tag, String message) {
    log(tag, message + "\n");
    logTextArea.setCaretPosition(logTextArea.getText().length());
  }
  
  /**
   * Removes the provided user
   * @param user User to remove
   */
  public void removeUser(User user) {
    if(users.containsKey(user.getId())) {
      int index = new ArrayList<Integer>(users.keySet()).indexOf(user.getId());
      ListDataEvent listDataEvent = new ListDataEvent(user, ListDataEvent.INTERVAL_REMOVED, index, index + 1);
      for(ListDataListener l : dataListeners)
        l.contentsChanged(listDataEvent);
      
      synchronized(users) {
        users.remove(user.getId());
      }
      logln("info", "Client " + user.getId() + " disconnected");
      eventServer.onClientDisconnect(user.getId());
      
    }
  }
  
  /**
   * Tries to stop the server
   */
  public boolean stopServer() {
    try {
      synchronized (users) {
        Iterator<User> userList = users.values().iterator();
        while(userList.hasNext()) {
          userList.next().socket.close();
        }
        users.clear();
      }
      this.socket.close();
      logln("info", "Server closed");
      return true;
    } catch (IOException e) {
      System.err.println("Could not close server");
      return false;
    }
  }

  /**
   * ActionListener requirement, handled interactions with components
   */
  @Override
  public void actionPerformed(ActionEvent event) {
    Object source = event.getSource();
    
    // handle when the stop button is pressed
    if (source == stopServerButton) {
      stopServer();
    }
    
    // A user in the user list is selected
    if(!userList.isSelectionEmpty()) {
      User user = userList.getSelectedValue();
          
      // handle when the removeUser button is pressed
      if (source == removeUserButton) {
        logln("info", "Kicking Client " + user.getId());
        removeUser(user);
      }
      
      if (source == renameUserButton) {
        String name = JOptionPane.showInputDialog("Rename User #" + user.getId());
        if(name.length() > 0) {
          logln("info", "Renamed Client " + user.getId() + " from '" + user.getName() + "' to '" + name + "'");
          setUserName(user.getId(), name);
        }
      }
      
    }
  }
  
  /**
   * Runnable method, handles creating new connections
   */
  @Override
  public void run() {
    while(!socket.isClosed()) {
      try {
        Socket client = socket.accept();
        User user = new User(client, this, ids);
        
        users.put(ids, user);
        logln("info", "Client " + ids + " connected");
        eventServer.onClientConnect(ids++);
        
        int index = new ArrayList<Integer>(users.keySet()).indexOf(user.getId());
        ListDataEvent listDataEvent = new ListDataEvent(user, ListDataEvent.INTERVAL_ADDED, index, index + 1);
        for(ListDataListener l : dataListeners)
          l.contentsChanged(listDataEvent);
        
        new Thread(user).start();
      } catch (IOException e) {
        System.err.println("Server Closed");
      }
            
    }
    
  }
  
  /**
   * The EventServer this Server is bound to
   * @return The EventServer this Server is bound to
   */
  public EventServer getEventServer() {
    return this.eventServer;
  }

  
}
